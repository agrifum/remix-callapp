package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE phoneKey = :phoneKey AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun getNotesForPhone(phoneKey: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE phoneKey = :phoneKey AND isArchived = 0 AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun getActiveNotesForPhone(phoneKey: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE phoneKey = :phoneKey AND isArchived = 0 AND deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getActiveNotesForPhoneSync(phoneKey: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isArchived = 0 AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun getAllActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE phoneKey = :phoneKey AND isArchived = 1 AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun getArchivedNotesForPhone(phoneKey: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun getNoteById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteByIdSync(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET isArchived = :isArchived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteNote(id: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreNote(id: String)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNotePermanently(id: String)

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeletedNotes(): Flow<List<NoteEntity>>

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffMillis")
    suspend fun purgeDeletedNotesOlderThan(cutoffMillis: Long): Int

    @Query("SELECT COUNT(*) FROM notes WHERE phoneKey = :phoneKey AND deletedAt IS NULL")
    suspend fun getNoteCountForPhone(phoneKey: String): Int

    @Query("SELECT DISTINCT phoneKey FROM notes WHERE deletedAt IS NULL")
    fun getAllPhoneKeysWithNotes(): Flow<List<String>>

    @Query("SELECT DISTINCT phoneKey FROM notes WHERE deletedAt IS NULL")
    suspend fun getAllPhoneKeysWithNotesSync(): List<String>
}
