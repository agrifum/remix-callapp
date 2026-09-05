package com.example.data.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.util.UUID

@Entity(tableName = "services")
data class ServiceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val defaultPriceMinor: Long? = null,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
