package com.example.data.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.example.core.model.WindowReason
import java.util.UUID

@Entity(
    tableName = "job_analysis_windows",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("jobId"),
        Index("endedAt")
    ]
)
data class JobAnalysisWindowEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val jobId: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val reason: WindowReason = WindowReason.CREATED,
    val lastAnalyzedSmsAt: Long? = null
)
