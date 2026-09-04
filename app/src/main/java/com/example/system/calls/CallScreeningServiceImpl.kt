package com.example.system.calls

import android.telecom.Call
import android.telecom.CallScreeningService
import com.example.core.model.CallDirection
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * CallScreeningServiceImpl: early incoming call identification.
 * Captures incoming phone number into ActiveCallSession and immediately responds to allow the call.
 * Performs no Room queries, AI, network calls, or blocking operations.
 */
@AndroidEntryPoint
class CallScreeningServiceImpl : CallScreeningService() {
    @Inject lateinit var callStateMonitor: CallStateMonitor

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle
        val rawNumber = handle?.schemeSpecificPart
        val isOutgoing = callDetails.callDirection == Call.Details.DIRECTION_OUTGOING
        val direction = if (isOutgoing) CallDirection.OUTGOING else CallDirection.INCOMING

        ActiveCallSession.setCall(
            rawNumber = rawNumber,
            direction = direction,
            timestamp = System.currentTimeMillis()
        )

        // respondToCall is required only for incoming calls; allow the call immediately without blocking
        if (!isOutgoing) {
            val response = CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .setSilenceCall(false)
                .setSkipCallLog(false)
                .setSkipNotification(false)
                .build()
            respondToCall(callDetails, response)
        }

        // Outside critical path of responding to call, ensure monitoring is registered
        callStateMonitor.start()
    }
}
