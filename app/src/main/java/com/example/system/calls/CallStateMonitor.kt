package com.example.system.calls

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.CallUppApplication
import com.example.core.model.CallDirection
import com.example.system.overlay.CallOverlayService
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * CallStateMonitor: Monitors telephony call states (RINGING, OFFHOOK, IDLE) using TelephonyCallback.CallStateListener.
 *
 * Architecture:
 * - CallScreeningService captures call identity (number + direction) -> ActiveCallSession
 * - CallStateMonitor listens to telephony state transitions via TelephonyCallback
 * - At OFFHOOK: Reads identity from ActiveCallSession and starts CallOverlayService (both incoming & outgoing)
 * - At IDLE: Clears ActiveCallSession, stops CallOverlayService, commits draft, and handles reengagement
 */
class CallStateMonitor(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    @Volatile
    private var isRegistered = false

    private var lastCallState = TelephonyManager.CALL_STATE_IDLE
    private var hasObservedActiveCallState = false
    private var currentSessionId: String? = null
    private var currentPhone: String? = null
    private var currentDirection: CallDirection = CallDirection.INCOMING
    private var callStartTime: Long = 0L

    // Hold a strong reference to the callback as required by Android TelephonyCallback lifecycle
    private val telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            handleCallStateChanged(state)
        }
    }

    /**
     * Idempotently starts call state monitoring.
     * Safely checks for READ_PHONE_STATE permission before registering.
     */
    @Synchronized
    fun start() {
        if (isRegistered) return
        val tm = telephonyManager ?: return

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            // Safe return without crash if permission is not yet granted
            return
        }

        try {
            tm.registerTelephonyCallback(context.mainExecutor, telephonyCallback)
            isRegistered = true
        } catch (_: SecurityException) {
            // Permission revoked concurrently or unavailable
        } catch (_: Exception) {
            // Other telephony registration errors
        }
    }

    /**
     * Safely unregisters the telephony callback if registered.
     */
    @Synchronized
    fun stop() {
        if (!isRegistered) return
        val tm = telephonyManager ?: return
        try {
            tm.unregisterTelephonyCallback(telephonyCallback)
        } catch (_: Exception) {
            // Ignore unregister exceptions
        } finally {
            isRegistered = false
        }
    }

    private fun handleCallStateChanged(state: Int) {
        val app = context.applicationContext as? CallUppApplication ?: return

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                hasObservedActiveCallState = true
                currentDirection = CallDirection.INCOMING

                // If ActiveCallSession already has valid incoming identity, preserve it
                val session = ActiveCallSession.get()
                if (session != null && !session.phoneKey.isNullOrBlank()) {
                    currentPhone = session.phoneKey
                }

                if (currentSessionId == null) {
                    currentSessionId = UUID.randomUUID().toString()
                }
                lastCallState = state
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                hasObservedActiveCallState = true
                val fallbackDirection = if (lastCallState == TelephonyManager.CALL_STATE_RINGING) {
                    CallDirection.INCOMING
                } else {
                    CallDirection.OUTGOING
                }

                // 1. Snapshot from ActiveCallSession
                val session = ActiveCallSession.get()

                // Direction: prefer session direction, with fallback to legacy ringing state
                val directionToUse = session?.direction ?: fallbackDirection
                currentDirection = directionToUse

                // Phone: prefer ActiveCallSession phoneKey, fallback to currentPhone
                val sessionPhone = session?.phoneKey?.takeIf { it.isNotBlank() }
                val phoneToUse = sessionPhone ?: currentPhone

                // Synchronize local currentPhone with resolved session phone
                if (!sessionPhone.isNullOrBlank()) {
                    currentPhone = sessionPhone
                }

                if (currentSessionId == null) {
                    currentSessionId = UUID.randomUUID().toString()
                }
                callStartTime = System.currentTimeMillis()
                lastCallState = state

                // Start native overlay only if permission is granted and a valid phone key exists
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
                        } catch (_: Exception) {
                            context.startService(overlayIntent)
                        }
                    } else {
                        context.startService(overlayIntent)
                    }
                }
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (!hasObservedActiveCallState) {
                    lastCallState = state
                    return
                }
                hasObservedActiveCallState = false

                ActiveCallSession.clear()

                val finishedSessionId = currentSessionId
                val finishedPhone = currentPhone
                val direction = currentDirection
                val startTime = callStartTime

                // Reset local state
                currentSessionId = null
                currentPhone = null
                lastCallState = state

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
