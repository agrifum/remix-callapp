package com.example.data.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.example.core.model.ReengagementSource
import com.example.core.model.ReengagementStatus
import java.util.UUID

@Entity(
    tableName = "reengagement_events",
    indices = [
        Index("clientId"),
        Index("status")
    ]
)
data class ReengagementEventEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val jobId: String,
    val source: ReengagementSource,
    val occurredAt: Long = System.currentTimeMillis(),
    val status: ReengagementStatus = ReengagementStatus.PENDING
)
