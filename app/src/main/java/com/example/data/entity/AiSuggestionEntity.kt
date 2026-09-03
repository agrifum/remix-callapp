package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.SuggestionStatus
import com.example.core.model.SuggestionType
import java.util.UUID

@Entity(
    tableName = "ai_suggestions",
    indices = [
        Index("clientId"),
        Index("targetJobId"),
        Index("status")
    ]
)
data class AiSuggestionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val targetJobId: String? = null,
    val type: SuggestionType,
    val proposedValueJson: String,
    val sourceSmsAt: Long,
    val status: SuggestionStatus = SuggestionStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null
)
