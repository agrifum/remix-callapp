package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.model.JobStatus
import com.example.core.model.WindowReason
import com.example.data.database.CallUppDatabase
import com.example.data.dao.JobAnalysisWindowDao
import com.example.data.dao.JobDao
import com.example.data.entity.JobAnalysisWindowEntity
import com.example.data.entity.JobEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import com.example.system.work.JobCompletionScheduler
import com.example.system.calendar.CalendarManager
import com.example.data.dao.ClientDao
import java.time.ZoneId
import java.util.UUID

class JobRepository(
    private val database: CallUppDatabase,
    private val jobDao: JobDao,
    private val windowDao: JobAnalysisWindowDao,
    private val scheduler: JobCompletionScheduler? = null,
    private val calendarManager: CalendarManager? = null,
    private val clientDao: ClientDao? = null
) {

    fun getJobsByStatus(status: JobStatus): Flow<List<JobEntity>> = jobDao.getJobsByStatus(status)

    fun getArchivedJobs(): Flow<List<JobEntity>> = jobDao.getArchivedJobs()

    fun getAllNonDeletedJobs(): Flow<List<JobEntity>> = jobDao.getAllNonDeletedJobs()

    fun getActiveJobsForClient(clientId: String): Flow<List<JobEntity>> = jobDao.getActiveJobsForClient(clientId)

    suspend fun getActiveJobsForClientSync(clientId: String): List<JobEntity> = jobDao.getActiveJobsForClientSync(clientId)

    fun getAllJobsForClient(clientId: String): Flow<List<JobEntity>> = jobDao.getAllJobsForClient(clientId)

    suspend fun getAllJobsForClientSync(clientId: String): List<JobEntity> = jobDao.getAllJobsForClientSync(clientId)

    fun getJobById(id: String): Flow<JobEntity?> = jobDao.getJobById(id)

    suspend fun getJobByIdSync(id: String): JobEntity? = jobDao.getJobByIdSync(id)

    suspend fun getLatestClosedOrCompletedJobForClient(clientId: String): JobEntity? =
        jobDao.getLatestClosedOrCompletedJobForClient(clientId)

    suspend fun getActiveJobsSync(): List<JobEntity> = jobDao.getActiveJobsSync()

    suspend fun createJob(job: JobEntity, openAnalysisWindow: Boolean = true): String {
        val jobId = if (job.id.isBlank()) UUID.randomUUID().toString() else job.id
        val finalJob = job.copy(id = jobId, updatedAt = System.currentTimeMillis())

        database.withTransaction {
            jobDao.insertJob(finalJob)
            if (openAnalysisWindow && finalJob.status == JobStatus.ACTIVE) {
                val window = JobAnalysisWindowEntity(
                    jobId = jobId,
                    startedAt = System.currentTimeMillis(),
                    reason = WindowReason.CREATED
                )
                windowDao.insertWindow(window)
            }
        }
        scheduler?.scheduleCompletion(finalJob)
        return jobId
    }

    suspend fun updateJob(job: JobEntity) {
        val updated = job.copy(updatedAt = System.currentTimeMillis())
        jobDao.updateJob(updated)
        if (updated.status == JobStatus.ACTIVE && updated.deletedAt == null) {
            scheduler?.scheduleCompletion(updated)
        } else {
            scheduler?.cancelCompletion(updated.id)
        }
        if (updated.calendarEventId != null && calendarManager != null) {
            try {
                val client = clientDao?.getClientByIdSync(updated.clientId)
                calendarManager.updateEvent(updated.calendarEventId, updated, client)
            } catch (_: Exception) {}
        }
    }

    suspend fun completeJob(jobId: String) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            jobDao.updateJobStatus(
                id = jobId,
                status = JobStatus.COMPLETED,
                updatedAt = now,
                completedAt = now
            )
            windowDao.closeAllWindowsForJob(jobId, now)
        }
        scheduler?.cancelCompletion(jobId)
    }

    suspend fun closeJob(jobId: String) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            jobDao.updateJobStatus(
                id = jobId,
                status = JobStatus.CLOSED,
                updatedAt = now,
                closedAt = now
            )
            windowDao.closeAllWindowsForJob(jobId, now)
        }
        scheduler?.cancelCompletion(jobId)
    }

    suspend fun reopenJob(jobId: String) {
        val now = System.currentTimeMillis()
        var reopenedJob: JobEntity? = null
        database.withTransaction {
            jobDao.updateJobStatus(
                id = jobId,
                status = JobStatus.ACTIVE,
                updatedAt = now,
                reopenedAt = now
            )
            val window = JobAnalysisWindowEntity(
                jobId = jobId,
                startedAt = now,
                reason = WindowReason.REOPENED
            )
            windowDao.insertWindow(window)
            reopenedJob = jobDao.getJobByIdSync(jobId)
        }
        if (reopenedJob != null) {
            scheduler?.scheduleCompletion(reopenedJob!!)
        }
    }

    suspend fun setJobArchived(jobId: String, archived: Boolean) {
        jobDao.setJobArchived(jobId, archived)
    }

    suspend fun softDeleteJob(jobId: String) {
        val now = System.currentTimeMillis()
        var jobToDelete: JobEntity? = null
        database.withTransaction {
            jobToDelete = jobDao.getJobByIdSync(jobId)
            jobDao.softDeleteJob(jobId, now)
            windowDao.closeAllWindowsForJob(jobId, now)
        }
        scheduler?.cancelCompletion(jobId)
        val eventId = jobToDelete?.calendarEventId
        if (eventId != null && calendarManager != null) {
            try {
                calendarManager.deleteEvent(eventId)
            } catch (_: Exception) {}
        }
    }

    suspend fun restoreJob(jobId: String) {
        var restoredJob: JobEntity? = null
        database.withTransaction {
            jobDao.restoreJob(jobId)
            val job = jobDao.getJobByIdSync(jobId)
            if (job != null && job.status == JobStatus.ACTIVE) {
                windowDao.insertWindow(
                    JobAnalysisWindowEntity(
                        jobId = jobId,
                        startedAt = System.currentTimeMillis(),
                        reason = WindowReason.REOPENED
                    )
                )
            }
            restoredJob = job
        }
        if (restoredJob != null && restoredJob!!.status == JobStatus.ACTIVE) {
            scheduler?.scheduleCompletion(restoredJob!!)
        }
    }

    suspend fun deletePermanently(jobId: String) {
        jobDao.deleteJobPermanently(jobId)
        scheduler?.cancelCompletion(jobId)
    }

    fun getDeletedJobs(): Flow<List<JobEntity>> = jobDao.getDeletedJobs()

    suspend fun checkHasDuplicateActiveTerm(
        clientId: String,
        currentJobId: String,
        dateEpochDay: Long?,
        timeMinute: Int?
    ): Boolean {
        if (dateEpochDay == null) return false
        val activeJobs = jobDao.getActiveJobsForClientSync(clientId)
        return activeJobs.any { other ->
            other.id != currentJobId &&
                other.preliminaryDateEpochDay == dateEpochDay &&
                (timeMinute == null || other.preliminaryTimeMinute == null || other.preliminaryTimeMinute == timeMinute)
        }
    }

    fun calculateCompletionAnchor(job: JobEntity): Long? = Companion.calculateCompletionAnchor(job)

    companion object {
        /**
         * Calculates anchor epoch millis for +24h auto-completion:
         * 1. confirmedStartAt
         * 2. preliminary date + time
         * 3. date only -> end of selected day (23:59:59)
         * 4. no date -> null
         */
        fun calculateCompletionAnchor(job: JobEntity): Long? {
            if (job.confirmedStartAt != null) {
                return job.confirmedStartAt
            }
            val dateEpochDay = job.preliminaryDateEpochDay ?: return null
            val date = LocalDate.ofEpochDay(dateEpochDay)
            val timeMinute = job.preliminaryTimeMinute
            val dateTime = if (timeMinute != null) {
                LocalDateTime.of(date, LocalTime.of(timeMinute / 60, timeMinute % 60))
            } else {
                LocalDateTime.of(date, LocalTime.of(23, 59, 59))
            }
            return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
}
