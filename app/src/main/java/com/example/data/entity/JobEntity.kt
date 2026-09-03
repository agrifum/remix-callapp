package com.example.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.EtaSource
import com.example.core.model.JobStatus
import com.example.core.model.TimeQualifier
import java.util.UUID

@Entity(
    tableName = "jobs",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("clientId"),
        Index("status"),
        Index("isArchived"),
        Index("deletedAt"),
        Index("confirmedStartAt")
    ]
)
data class JobEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val clientId: String,
    val serviceId: String? = null,
    val serviceNameSnapshot: String? = null,
    val priceMinor: Long? = null,

    val preliminaryDateEpochDay: Long? = null,
    val preliminaryTimeMinute: Int? = null,
    val preliminaryTimeQualifier: TimeQualifier = TimeQualifier.EXACT,
    val confirmedStartAt: Long? = null,

    val addressCitySnapshot: String? = null,
    val addressDistrictSnapshot: String? = null,
    val addressStreetSnapshot: String? = null,
    val addressBuildingSnapshot: String? = null,
    val addressUnitSnapshot: String? = null,
    val addressPostalCodeSnapshot: String? = null,

    val manualNotes: String? = null,
    val additionalInfo: String? = null,
    val smsSummary: String? = null,

    val status: JobStatus = JobStatus.ACTIVE,
    val isArchived: Boolean = false,
    val deletedAt: Long? = null,
    val calendarEventId: Long? = null,

    val predictedArrivalAt: Long? = null,
    val etaSource: EtaSource? = null,
    val etaUpdatedAt: Long? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val closedAt: Long? = null,
    val reopenedAt: Long? = null
)
