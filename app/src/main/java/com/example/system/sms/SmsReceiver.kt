package com.example.system.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.CallUppApplication
import com.example.core.model.ReengagementSource
import com.example.core.model.SmsAnalysisMode
import com.example.core.model.TriggerState
import com.example.core.phone.PhoneNumberNormalizer
import com.example.data.entity.SmsTriggerEntity
import com.example.system.work.SmsAnalysisWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * SmsReceiver: Listens for SMS_RECEIVED.
 * Acts solely as a lightweight metadata-only eligibility and trigger layer.
 * Raw SMS body is NEVER read, joined, parsed, or persisted by this receiver.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) return

        // Extract metadata ONLY (originating address and timestamp). Do NOT read raw SMS payload.
        val originatingAddress = msgs[0].originatingAddress ?: return
        val phoneKey = PhoneNumberNormalizer.normalizeKey(originatingAddress)
        if (phoneKey.isBlank()) return
        val timestamp = msgs[0].timestampMillis

        val app = context.applicationContext as? CallUppApplication ?: return
        val pendingResult = goAsync()

        app.container.appScope.launch {
            try {
                // 1. Check global preference WITHOUT reading SMS body
                val globalEnabled = app.container.appPreferences.smsAnalysisGlobalEnabled.first()
                if (!globalEnabled) return@launch

                // 2. Resolve client and check effective SMS analysis mode WITHOUT reading SMS body
                val client = app.container.clientRepository.getClientByPhoneKeySync(phoneKey) ?: return@launch
                if (client.smsAnalysisMode == SmsAnalysisMode.DISABLED) return@launch

                // 3. Client must have active jobs with open analysis window covering received timestamp
                val activeJobs = app.container.jobRepository.getActiveJobsForClientSync(client.id)
                if (activeJobs.isEmpty()) {
                    // Check if reengagement applies (no active jobs, but has closed/completed jobs)
                    app.container.reengagementRepository.checkAndCreateReengagementEvent(
                        clientId = client.id,
                        source = ReengagementSource.INCOMING_SMS
                    )
                    return@launch
                }

                var hasEligibleWindow = false
                for (job in activeJobs) {
                    val window = app.container.windowDao.getOpenWindowForJob(job.id)
                    if (window != null && window.endedAt == null && timestamp >= window.startedAt) {
                        hasEligibleWindow = true
                        break
                    }
                }
                if (!hasEligibleWindow) return@launch

                // 4. Create metadata-only trigger record (storing metadata only, NOT raw SMS text in Room)
                val triggerId = UUID.randomUUID().toString()
                val trigger = SmsTriggerEntity(
                    id = triggerId,
                    clientId = client.id,
                    senderPhoneKey = phoneKey,
                    receivedAt = timestamp,
                    state = TriggerState.PENDING
                )
                app.container.smsTriggerDao.insertTrigger(trigger)

                // 5. Delegate analysis to WorkManager (dedicated SmsAnalysisWorker)
                val workRequest = OneTimeWorkRequestBuilder<SmsAnalysisWorker>()
                    .setInputData(workDataOf(SmsAnalysisWorker.KEY_TRIGGER_ID to triggerId))
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "sms_analysis_$triggerId",
                    ExistingWorkPolicy.KEEP,
                    workRequest
                )
            } catch (_: Exception) {
                // Fail-safe non-blocking execution
            } finally {
                pendingResult.finish()
            }
        }
    }
}
