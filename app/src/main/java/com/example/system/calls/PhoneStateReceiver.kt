package com.example.system.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import com.example.CallUppApplication
import com.example.core.model.CallDirection
import com.example.core.phone.PhoneNumberNormalizer
import com.example.system.overlay.CallOverlayService
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * PhoneStateReceiver: Listens for telephony state transitions (RINGING, OFFHOOK, IDLE)
 * to show/hide the CallOverlayService and auto-commit unsaved notes when call ends.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var currentSessionId: String? = null
        private var currentPhone: String? = null
        private var currentDirection: CallDirection = CallDirection.INCOMING
        private var callStartTime: Long = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (!incomingNumber.isNullOrBlank()) {
            currentPhone = PhoneNumberNormalizer.normalizeKey(incomingNumber)
        }

        val app = context.applicationContext as? CallUppApplication ?: return

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                currentDirection = CallDirection.INCOMING
                ActiveCallSession.updateCall(
                    rawNumber = incomingNumber,
                    direction = CallDirection.INCOMING
                )
                if (currentSessionId == null) {
                    currentSessionId = UUID.randomUUID().toString()
                }
                lastState = stateStr
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val fallbackDirection = if (lastState == TelephonyManager.EXTRA_STATE_RINGING) {
                    CallDirection.INCOMING
                } else {
                    CallDirection.OUTGOING
                }

                // 1. Snapshot from ActiveCallSession
                val session = ActiveCallSession.get()

                // 3. Determine call direction: prefer session direction, with fallback to legacy ringing check
                val directionToUse = session?.direction ?: fallbackDirection
                currentDirection = directionToUse

                ActiveCallSession.updateCall(
                    rawNumber = incomingNumber,
                    direction = currentDirection
                )

                val updatedSession = ActiveCallSession.get()

                // 2. Determine phone key: prefer ActiveCallSession, fallback to currentPhone without overwriting with null/blank
                val sessionPhone = updatedSession?.phoneKey?.takeIf { it.isNotBlank() }
                    ?: session?.phoneKey?.takeIf { it.isNotBlank() }
                val phoneToUse = sessionPhone ?: currentPhone

                // 5. Synchronize local currentPhone with resolved session phone
                if (!sessionPhone.isNullOrBlank()) {
                    currentPhone = sessionPhone
                }

                if (currentSessionId == null) {
                    currentSessionId = UUID.randomUUID().toString()
                }
                callStartTime = System.currentTimeMillis()
                lastState = stateStr

                // 4. Pass resolved phone number and direction to CallOverlayService
                if (Settings.canDrawOverlays(context) && !phoneToUse.isNullOrBlank()) {
                    val overlayIntent = Intent(context, CallOverlayService::class.java).apply {
                        action = CallOverlayService.ACTION_SHOW_OVERLAY
                        putExtra(CallOverlayService.EXTRA_CALL_SESSION_ID, currentSessionId)
                        putExtra(CallOverlayService.EXTRA_PHONE_KEY, phoneToUse)
                        putExtra(CallOverlayService.EXTRA_CALL_DIRECTION, currentDirection.name)
                        putExtra(CallOverlayService.EXTRA_CALL_TIMESTAMP, callStartTime)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            context.startForegroundService(overlayIntent)
                        } catch (e: Exception) {
                            // Fallback if background start restricted
                            context.startService(overlayIntent)
                        }
                    } else {
                        context.startService(overlayIntent)
                    }
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                ActiveCallSession.clear()

                val finishedSessionId = currentSessionId
                val finishedPhone = currentPhone
                val direction = currentDirection
                val startTime = callStartTime

                // Reset state
                currentSessionId = null
                currentPhone = null
                lastState = stateStr

                // Flush latest in-memory draft from overlay before stopping service
                val latestDraft = if (finishedSessionId != null) {
                    CallOverlayService.flushAndStop(context, finishedSessionId)
                } else {
                    context.stopService(Intent(context, CallOverlayService::class.java))
                    null
                }

                // Invariant: If call ends without manual save, commit non-empty draft note text
                if (finishedSessionId != null) {
                    app.container.appScope.launch {
                        app.container.callDraftRepository.flushAndCommitOnCallEnd(
                            callSessionId = finishedSessionId,
                            latestDraft = latestDraft,
                            callDirection = direction,
                            callTime = startTime
                        )

                        // Check for reengagement event if incoming call from client with completed/closed jobs
                        if (!finishedPhone.isNullOrBlank() && direction == CallDirection.INCOMING) {
                            val client = app.container.clientRepository.getClientByPhoneKeySync(finishedPhone)
                            if (client != null) {
                                app.container.reengagementRepository.checkAndCreateReengagementEvent(
                                    clientId = client.id,
                                    source = com.example.core.model.ReengagementSource.INCOMING_CALL
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
