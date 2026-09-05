package com.example.system.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.core.model.ReengagementSource
import com.example.core.model.SmsAnalysisMode
import com.example.core.model.TriggerState
import com.example.core.phone.PhoneNumberNormalizer
import com.example.data.entity.SmsTriggerEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.example.data.dao.JobAnalysisWindowDao
import com.example.data.dao.SmsTriggerDao
import com.example.data.preferences.AppPreferences
import com.example.data.repository.ClientRepository
import com.example.data.repository.JobRepository
import com.example.data.repository.ReengagementRepository
import com.example.system.work.SmsTriggerRecovery
import kotlinx.coroutines.CoroutineScope
import java.util.UUID

/**
 * SmsReceiver: Listens for SMS_RECEIVED.
 * Acts solely as a lightweight metadata-only eligibility and trigger layer.
 *
 * Strict Privacy Contract:
 * - When Global SMS Analysis is OFF or Client SMS Analysis is DISABLED, AI trigger creation and WorkManager analysis are blocked;
 *   client reengagement for inactive clients is evaluated using metadata only without accessing message body.
 * - Raw SMS body is NEVER read, joined, parsed, or persisted by this receiver.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {
    @Inject lateinit var appScope: CoroutineScope
    @Inject lateinit var clientRepository: ClientRepository
    @Inject lateinit var jobRepository: JobRepository
    @Inject lateinit var reengagementRepository: ReengagementRepository
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var windowDao: JobAnalysisWindowDao
    @Inject lateinit var smsTriggerDao: SmsTriggerDao
    @Inject lateinit var smsTriggerRecovery: SmsTriggerRecovery

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()

        appScope.launch {
            try {
                // 1. Parse SMS metadata ONLY (originating address and timestamp). Do NOT read raw SMS payload.
                val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (msgs.isNullOrEmpty()) return@launch

                // Extract metadata ONLY (originating address and timestamp). Do NOT read raw SMS payload.
                val originatingAddress = msgs[0].originatingAddress ?: return@launch
                val phoneKey = PhoneNumberNormalizer.normalizeKey(originatingAddress)
                if (phoneKey.isBlank()) return@launch
                val timestamp = msgs[0].timestampMillis

                // 2. Resolve client WITHOUT reading SMS body
                val client = clientRepository.getClientByPhoneKeySync(phoneKey) ?: return@launch

                // 3. Inspect ACTIVE jobs. If no active jobs, evaluate reengagement unconditionally (no AI needed)
                val activeJobs = jobRepository.getActiveJobsForClientSync(client.id)
                if (activeJobs.isEmpty()) {
                    // Reengagement does not require AI analysis or SMS body read
                    reengagementRepository.checkAndCreateReengagementEvent(
                        clientId = client.id,
                        source = ReengagementSource.INCOMING_SMS
                    )
                    return@launch
                }

                // 4. ACTIVE jobs exist: AI analysis settings gate trigger & worker creation
                val globalEnabled = appPreferences.smsAnalysisGlobalEnabled.first()
                if (!globalEnabled) return@launch

                if (client.smsAnalysisMode == SmsAnalysisMode.DISABLED) return@launch

                // 5. Client must have active jobs with open analysis window covering received timestamp
                var hasEligibleWindow = false
                for (job in activeJobs) {
                    val window = windowDao.getOpenWindowForJob(job.id)
                    if (window != null && window.endedAt == null && timestamp >= window.startedAt) {
                        hasEligibleWindow = true
                        break
                    }
                }
                if (!hasEligibleWindow) return@launch

                // 5. Create metadata-only trigger record (storing metadata only, NOT raw SMS text in Room)
                val triggerId = UUID.randomUUID().toString()
                val trigger = SmsTriggerEntity(
                    id = triggerId,
                    clientId = client.id,
                    senderPhoneKey = phoneKey,
                    receivedAt = timestamp,
                    state = TriggerState.PENDING
                )
                smsTriggerDao.insertTrigger(trigger)

                // 6. Delegate analysis to WorkManager via SmsTriggerRecovery helper
                smsTriggerRecovery.enqueueOrDiscard(trigger)
            } catch (_: Exception) {
                // Fail-safe non-blocking execution
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
