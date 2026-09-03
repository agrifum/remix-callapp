package com.example.data.repository

import com.example.core.phone.PhoneNumberNormalizer
import com.example.data.dao.NoteDao
import com.example.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {

    fun getNotesForPhone(phone: String): Flow<List<NoteEntity>> {
        val key = PhoneNumberNormalizer.normalizeKey(phone)
        return noteDao.getNotesForPhone(key)
    }

    fun getActiveNotesForPhone(phone: String): Flow<List<NoteEntity>> {
        val key = PhoneNumberNormalizer.normalizeKey(phone)
        return noteDao.getActiveNotesForPhone(key)
    }

    suspend fun getActiveNotesForPhoneSync(phone: String): List<NoteEntity> {
        val key = PhoneNumberNormalizer.normalizeKey(phone)
        return noteDao.getActiveNotesForPhoneSync(key)
    }

    val allActiveNotes: Flow<List<NoteEntity>> = noteDao.getAllActiveNotes()

    fun getArchivedNotesForPhone(phone: String): Flow<List<NoteEntity>> {
        val key = PhoneNumberNormalizer.normalizeKey(phone)
        return noteDao.getArchivedNotesForPhone(key)
    }

    fun getNoteById(id: String): Flow<NoteEntity?> = noteDao.getNoteById(id)

    suspend fun getNoteByIdSync(id: String): NoteEntity? = noteDao.getNoteByIdSync(id)

    suspend fun insertNote(note: NoteEntity) {
        val key = PhoneNumberNormalizer.normalizeKey(note.phoneKey)
        noteDao.insertNote(note.copy(phoneKey = key, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateNote(note: NoteEntity) {
        val key = PhoneNumberNormalizer.normalizeKey(note.phoneKey)
        noteDao.updateNote(note.copy(phoneKey = key, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setArchived(id: String, isArchived: Boolean) {
        noteDao.setArchived(id, isArchived)
    }

    suspend fun softDeleteNote(id: String) {
        noteDao.softDeleteNote(id)
    }

    suspend fun restoreNote(id: String) {
        noteDao.restoreNote(id)
    }

    suspend fun deletePermanently(id: String) {
        noteDao.deleteNotePermanently(id)
    }

    fun getDeletedNotes(): Flow<List<NoteEntity>> = noteDao.getDeletedNotes()

    suspend fun hasNotesForPhone(phone: String): Boolean {
        val key = PhoneNumberNormalizer.normalizeKey(phone)
        return noteDao.getNoteCountForPhone(key) > 0
    }
}
