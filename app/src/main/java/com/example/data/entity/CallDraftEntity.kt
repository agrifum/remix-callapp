package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_drafts")
data class CallDraftEntity(
    @PrimaryKey val callSessionId: String,
    val phoneKey: String,
    val noteText: String = "",
    val markAsClient: Boolean = false,
    val createJob: Boolean = false,
    val serviceId: String? = null,
    val preliminaryDateEpochDay: Long? = null,
    val preliminaryTimeMinute: Int? = null,
    val taskRequested: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
