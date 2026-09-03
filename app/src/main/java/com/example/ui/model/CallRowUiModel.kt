package com.example.ui.model

import com.example.core.model.CallDirection

enum class CallRowDirection {
    INCOMING,
    OUTGOING,
    MISSED
}

data class CallRowUiModel(
    val id: String,
    val phoneKey: String,
    val displayNumber: String,
    val contactOrClientDisplayName: String?,
    val timestamp: Long,
    val direction: CallRowDirection,
    val durationSeconds: Long,
    val isClient: Boolean,
    val hasNotes: Boolean,
    val clientId: String? = null
)
