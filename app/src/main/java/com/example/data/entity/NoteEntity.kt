package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
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
