package com.example.system.calls

import com.example.core.model.CallDirection
import com.example.core.phone.PhoneNumberNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight, in-memory state representing the currently active phone call.
 * Contains only call identity and direction metadata.
 * Does not store Client data, Notes, Jobs, Tasks, SMS, or AI data.
 * Not persisted to Room.
 */
data class ActiveCall(
    val phoneKey: String?,
    val rawNumber: String?,
    val displayNumber: String?,
    val direction: CallDirection,
    val timestamp: Long = System.currentTimeMillis()
)

object ActiveCallSession {

    private val _currentCall = MutableStateFlow<ActiveCall?>(null)
    val currentCall: StateFlow<ActiveCall?> = _currentCall.asStateFlow()

    /**
     * Reads the current active call identity.
     */
    fun get(): ActiveCall? = _currentCall.value

    /**
     * Sets the current call identity for a NEW active call, fully replacing previous active-call identity.
     * When a usable raw number exists:
     * - preserves it as rawNumber
     * - calculates phoneKey with PhoneNumberNormalizer.normalizeKey(...)
     * - calculates displayNumber separately with PhoneNumberNormalizer.formatDisplay(...)
     * When no usable phone number exists:
     * - phoneKey = null, rawNumber = null, displayNumber = null
     */
    @Synchronized
    fun setCall(
        rawNumber: String?,
        direction: CallDirection = CallDirection.INCOMING,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val trimmedRaw = rawNumber?.trim()?.ifBlank { null }
        val normalized = if (trimmedRaw != null) PhoneNumberNormalizer.normalizeKey(trimmedRaw).ifBlank { null } else null
        val display = if (normalized != null) PhoneNumberNormalizer.formatDisplay(trimmedRaw ?: normalized).ifBlank { null } else null

        _currentCall.value = ActiveCall(
            phoneKey = normalized,
            rawNumber = if (normalized != null) trimmedRaw else null,
            displayNumber = display,
            direction = direction,
            timestamp = timestamp
        )
    }

    /**
     * Updates the current call identity.
     * Preserves existing valid phone numbers if the new rawNumber is blank/null.
     */
    @Synchronized
    fun updateCall(
        rawNumber: String?,
        direction: CallDirection? = null,
        timestamp: Long? = null
    ) {
        val existing = _currentCall.value
        val trimmedRaw = rawNumber?.trim()?.ifBlank { null }
        val normalized = if (trimmedRaw != null) PhoneNumberNormalizer.normalizeKey(trimmedRaw).ifBlank { null } else null

        if (existing == null) {
            setCall(
                rawNumber = rawNumber,
                direction = direction ?: CallDirection.INCOMING,
                timestamp = timestamp ?: System.currentTimeMillis()
            )
            return
        }

        val finalKey = normalized ?: existing.phoneKey
        val finalRaw = if (normalized != null) trimmedRaw else existing.rawNumber
        val finalDisplay = if (finalKey != null) {
            PhoneNumberNormalizer.formatDisplay(finalRaw ?: finalKey).ifBlank { null }
        } else {
            null
        }

        _currentCall.value = existing.copy(
            phoneKey = finalKey,
            rawNumber = finalRaw,
            displayNumber = finalDisplay,
            direction = direction ?: existing.direction,
            timestamp = timestamp ?: existing.timestamp
        )
    }

    /**
     * Clears the current active call session.
     */
    @Synchronized
    fun clear() {
        _currentCall.value = null
    }
}
