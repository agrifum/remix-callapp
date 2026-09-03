package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.core.model.NameSource
import com.example.core.model.SmsAnalysisMode
import java.util.UUID

@Entity(
    tableName = "clients",
    indices = [Index(value = ["phoneKey"], unique = true)]
)
data class ClientEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val phoneKey: String,
    val phoneDisplay: String,
    val displayName: String,
    val nameSource: NameSource = NameSource.AUTO,
    val firstName: String? = null,
    val lastName: String? = null,
    val nip: String? = null,
    val city: String? = null,
    val district: String? = null,
    val street: String? = null,
    val buildingNumber: String? = null,
    val unitNumber: String? = null,
    val postalCode: String? = null,
    val additionalInfo: String? = null,
    val smsAnalysisMode: SmsAnalysisMode = SmsAnalysisMode.INHERIT,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
