package com.example.data.repository

import com.example.core.model.TaskStatus
import com.example.core.phone.PhoneNumberNormalizer
import com.example.data.dao.TaskDao
import com.example.data.dao.TaskWithNote
import com.example.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class TaskRepository(private val taskDao: TaskDao) {

    val allActiveTasks: Flow<List<TaskWithNote>> = taskDao.getAllActiveTasksWithNotes()
    val openTasks: Flow<List<TaskWithNote>> = taskDao.getOpenTasksWithNotes()

    fun getTasksForPhone(phone: String): Flow<List<TaskWithNote>> {
        val key = PhoneNumberNormalizer.normalizeKey(phone)
        return taskDao.getTasksForPhone(key)
    }

    suspend fun createTask(noteId: String): String {
        val id = UUID.randomUUID().toString()
        val task = TaskEntity(
            id = id,
            noteId = noteId,
            status = TaskStatus.OPEN,
            createdAt = System.currentTimeMillis()
        )
        taskDao.insertTask(task)
        return id
    }

    suspend fun setTaskStatus(id: String, status: TaskStatus) {
        val completedAt = if (status == TaskStatus.DONE) System.currentTimeMillis() else null
        taskDao.setTaskStatus(id, status, completedAt)
    }

    suspend fun softDeleteTask(id: String) {
        taskDao.softDeleteTask(id)
    }

    suspend fun restoreTask(id: String) {
        taskDao.restoreTask(id)
    }

    suspend fun deletePermanently(id: String) {
        taskDao.deleteTaskPermanently(id)
    }

    fun getDeletedTasks(): Flow<List<TaskWithNote>> = taskDao.getDeletedTasksWithNotes()
}
