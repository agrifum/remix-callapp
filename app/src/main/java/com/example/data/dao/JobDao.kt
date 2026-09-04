package com.example.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.example.core.model.JobStatus
import com.example.data.entity.JobEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {

    @Query("SELECT * FROM jobs WHERE status = :status AND isArchived = 0 AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun getJobsByStatus(status: JobStatus): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE isArchived = 1 AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun getArchivedJobs(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun getAllNonDeletedJobs(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE clientId = :clientId AND status = 'ACTIVE' AND isArchived = 0 AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun getActiveJobsForClient(clientId: String): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE clientId = :clientId AND status = 'ACTIVE' AND isArchived = 0 AND deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getActiveJobsForClientSync(clientId: String): List<JobEntity>

    @Query("SELECT * FROM jobs WHERE clientId = :clientId AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun getAllJobsForClient(clientId: String): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE clientId = :clientId AND deletedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getAllJobsForClientSync(clientId: String): List<JobEntity>

    @Query("SELECT * FROM jobs WHERE id = :id LIMIT 1")
    fun getJobById(id: String): Flow<JobEntity?>

    @Query("SELECT * FROM jobs WHERE id = :id LIMIT 1")
    suspend fun getJobByIdSync(id: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE clientId = :clientId AND status IN ('COMPLETED', 'CLOSED') AND deletedAt IS NULL ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestClosedOrCompletedJobForClient(clientId: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE status = 'ACTIVE' AND deletedAt IS NULL")
    suspend fun getActiveJobsSync(): List<JobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobEntity)

    @Update
    suspend fun updateJob(job: JobEntity)

    @Query("""
        UPDATE jobs
        SET status = :status, updatedAt = :updatedAt, completedAt = :completedAt, closedAt = :closedAt, reopenedAt = :reopenedAt
        WHERE id = :id
    """)
    suspend fun updateJobStatus(
        id: String,
        status: JobStatus,
        updatedAt: Long = System.currentTimeMillis(),
        completedAt: Long? = null,
        closedAt: Long? = null,
        reopenedAt: Long? = null
    )

    @Query("UPDATE jobs SET isArchived = :isArchived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setJobArchived(id: String, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE jobs SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDeleteJob(id: String, deletedAt: Long = System.currentTimeMillis())

    @Query("UPDATE jobs SET deletedAt = NULL WHERE id = :id")
    suspend fun restoreJob(id: String)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun deleteJobPermanently(id: String)

    @Query("SELECT * FROM jobs WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun getDeletedJobs(): Flow<List<JobEntity>>

    @Query("DELETE FROM jobs WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffMillis")
    suspend fun purgeDeletedJobsOlderThan(cutoffMillis: Long): Int
}
