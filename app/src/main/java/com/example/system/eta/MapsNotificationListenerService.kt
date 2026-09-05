package com.example.system.eta

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.core.model.EtaSource
import com.example.data.preferences.AppPreferences
import com.example.data.repository.JobRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.regex.Pattern

/**
 * MapsNotificationListenerService:
 * Passively extracts ETA info from Google Maps turn-by-turn navigation notifications
 * and attaches it to the currently active job without requesting fine/coarse GPS location.
 */
@AndroidEntryPoint
class MapsNotificationListenerService : NotificationListenerService() {
    @Inject lateinit var appScope: CoroutineScope
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var jobRepository: JobRepository

    // Common patterns in Polish Maps notifications: "Za 15 min", "15 min (8,2 km) • 14:30"
    private val timePattern = Pattern.compile("(\\d{1,2})\\s*(?:min|godz)")

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val packageName = sbn.packageName ?: return
        if (!packageName.contains("google.android.apps.maps")) return

        appScope.launch {
            val enabled = appPreferences.mapsEtaParsingEnabled.first()
            if (!enabled) return@launch

            val extras = sbn.notification.extras ?: return@launch
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val title = extras.getCharSequence("android.title")?.toString() ?: ""
            val combined = "$title $text"

            // Estimate duration in minutes
            val matcher = timePattern.matcher(combined)
            var minutesOffset: Long? = null
            if (matcher.find()) {
                val value = matcher.group(1)?.toLongOrNull()
                if (value != null) {
                    minutesOffset = if (combined.contains("godz")) value * 60 else value
                }
            }

            if (minutesOffset != null && minutesOffset > 0) {
                val predictedArrival = System.currentTimeMillis() + (minutesOffset * 60 * 1000)
                // Attach to the earliest scheduled or open active job
                val activeJobs = jobRepository.getActiveJobsSync()
                val targetJob = activeJobs.firstOrNull()
                if (targetJob != null) {
                    jobRepository.updateJob(
                        targetJob.copy(
                            predictedArrivalAt = predictedArrival,
                            etaSource = EtaSource.MAPS_NOTIFICATION,
                            etaUpdatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }
}
