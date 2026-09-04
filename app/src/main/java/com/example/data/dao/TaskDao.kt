package com.example.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.example.core.model.TaskStatus
import com.example.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

data class TaskWithNote(
    val taskId: String,
    val noteId: String,
    val status: TaskStatus,
    val isArchived: Boolean,
    val deletedAt: Long?,
    val createdAt: Long,
    val completedAt: Long?,
    val phoneKey: String,
    val noteText: String
)

@Dao
interface TaskDao {

    @Query("""
        SELECT t.id as taskId, t.noteId, t.status, t.isArchived, t.deletedAt, t.createdAt, t.completedAt,
               n.phoneKey, n.rawText as noteText
        FROM tasks t
        INNER JOIN notes n ON t.noteId = n.id
        WHERE t.deletedAt IS NULL AND t.isArchived = 0
        ORDER BY t.status ASC, t.createdAt DESC
    """)
    fun getAllActiveTasksWithNotes(): Flow<List<TaskWithNote>>

    @Query("""
        SELECT t.id as taskId, t.noteId, t.status, t.isArchived, t.deletedAt, t.createdAt, t.completedAt,
               n.phoneKey, n.rawText as noteText
        FROM tasks t
        INNER JOIN notes n ON t.noteId = n.id
        WHERE t.deletedAt IS NULL AND t.isArchived = 0 AND t.status = 'OPEN'
        ORDER BY t.createdAt DESC
    """)
    fun getOpenTasksWithNotes(): Flow<List<TaskWithNote>>

    @Query("""
        SELECT t.id as taskId, t.noteId, t.status, t.isArchived, t.deletedAt, t.createdAt, t.completedAt,
               n.phoneKey, n.rawText as noteText
        FROM tasks t
        INNER JOIN notes n ON t.noteId = n.id
        WHERE n.phoneKey = :phoneKey AND t.deletedAt IS NULL
        ORDER BY t.createdAt DESC
    """)
    fun getTasksForPhone(phoneKey: String): Flow<List<TaskWithNote>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun getTaskById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksSync(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun setTaskStatus(id: String, status: TaskStatus, completedAt: Long?)

    @Query("UPDATE tasks SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteTask(id: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreTask(id: String)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskPermanently(id: String)

    @Query("""
        SELECT t.id as taskId, t.noteId, t.status, t.isArchived, t.deletedAt, t.createdAt, t.completedAt,
               n.phoneKey, n.rawText as noteText
        FROM tasks t
        INNER JOIN notes n ON t.noteId = n.id
        WHERE t.deletedAt IS NOT NULL
        ORDER BY t.deletedAt DESC
    """)
    fun getDeletedTasksWithNotes(): Flow<List<TaskWithNote>>

    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffMillis")
    suspend fun purgeDeletedTasksOlderThan(cutoffMillis: Long): Int
}
