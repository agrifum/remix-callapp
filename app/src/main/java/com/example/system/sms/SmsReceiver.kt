package com.example.system.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.CallUppApplication
import com.example.core.model.SmsAnalysisMode
import com.example.core.model.TriggerState
import com.example.core.phone.PhoneNumberNormalizer
import com.example.data.entity.SmsTriggerEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * SmsReceiver: Listens for SMS_RECEIVED.
 * Raw SMS body is NEVER stored in the database! It only triggers analysis if the client has an active job window.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (msgs.isNullOrEmpty()) return

        val originatingAddress = msgs[0].originatingAddress ?: return
        val phoneKey = PhoneNumberNormalizer.normalizeKey(originatingAddress)
        val fullBody = msgs.joinToString("") { it.messageBody ?: "" }
        val timestamp = msgs[0].timestampMillis

        val app = context.applicationContext as? CallUppApplication ?: return

        app.container.appScope.launch {
            // 1. Check global preference
            val globalEnabled = app.container.appPreferences.smsAnalysisGlobalEnabled.first()
            if (!globalEnabled) return@launch

            // 2. Check if client exists
            val client = app.container.clientRepository.getClientByPhoneKeySync(phoneKey) ?: return@launch
            if (client.smsAnalysisMode == SmsAnalysisMode.DISABLED) return@launch

            // 3. Client must have active jobs with open analysis window
            val activeJobs = app.container.jobRepository.getActiveJobsForClientSync(client.id)
            if (activeJobs.isEmpty()) {
                // Check if reengagement applies (no active jobs, but has closed/completed jobs)
                app.container.reengagementRepository.checkAndCreateReengagementEvent(
                    clientId = client.id,
                    source = com.example.core.model.ReengagementSource.INCOMING_SMS
                )
                return@launch
            }

            // 4. Create trigger record (storing metadata only, NOT the raw SMS text in Room)
            val triggerId = UUID.randomUUID().toString()
            val trigger = SmsTriggerEntity(
                id = triggerId,
                clientId = client.id,
                senderPhoneKey = phoneKey,
                receivedAt = timestamp,
                state = TriggerState.PENDING
            )
            app.container.smsTriggerDao.insertTrigger(trigger)

            // 5. Run SMS extraction in-memory safely
            app.container.smsAnalysisCoordinator.processSmsTrigger(triggerId, fullBody)
        }
    }
}
