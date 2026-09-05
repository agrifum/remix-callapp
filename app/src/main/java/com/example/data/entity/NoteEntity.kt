package com.example.data.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.example.core.model.CallDirection
import com.example.core.model.NoteSource
import java.util.UUID

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["phoneKey", "isArchived", "createdAt"])
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val phoneKey: String,
    val rawText: String,
    val source: NoteSource = NoteSource.CALL,
    val sourceCallDirection: CallDirection? = null,
    val sourceCallAt: Long? = null,
    val isArchived: Boolean = false,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
