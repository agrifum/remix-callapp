package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sms_templates")
data class SmsTemplateEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val body: String,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
