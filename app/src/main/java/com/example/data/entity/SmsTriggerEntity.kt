package com.example.data.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.example.core.model.TriggerState
import java.util.UUID

@Entity(
    tableName = "sms_triggers",
    indices = [
        Index("clientId"),
        Index("senderPhoneKey"),
        Index("state")
    ]
)
data class SmsTriggerEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val senderPhoneKey: String,
    val receivedAt: Long,
    val state: TriggerState = TriggerState.PENDING,
    val attemptCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
