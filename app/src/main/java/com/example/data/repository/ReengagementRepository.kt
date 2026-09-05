package com.example.data.repository

import androidx.room3.withWriteTransaction
import com.example.core.model.JobStatus
import com.example.core.model.ReengagementSource
import com.example.core.model.ReengagementStatus
import com.example.core.model.WindowReason
import com.example.data.database.CallUppDatabase
import com.example.data.dao.JobAnalysisWindowDao
import com.example.data.dao.JobDao
import com.example.data.dao.ReengagementEventDao
import com.example.data.entity.JobEntity
import com.example.data.entity.JobAnalysisWindowEntity
import com.example.data.entity.ReengagementEventEntity
import com.example.system.work.JobCompletionScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ReengagementRepository(
    private val database: CallUppDatabase,
    private val reengagementDao: ReengagementEventDao,
    private val jobDao: JobDao,
    private val windowDao: JobAnalysisWindowDao,
    private val scheduler: JobCompletionScheduler? = null
) {

    private val reengagementMutex = Mutex()

    val pendingEvents: Flow<List<ReengagementEventEntity>> = reengagementDao.getPendingEvents()

    suspend fun checkAndCreateReengagementEvent(clientId: String, source: ReengagementSource) {
        reengagementMutex.withLock {
            database.withWriteTransaction {
                val activeJobs = jobDao.getActiveJobsForClientSync(clientId)
                if (activeJobs.isNotEmpty()) {
                    // Already has active jobs, no reengagement needed
                    return@withWriteTransaction
                }

                val pastJob = jobDao.getLatestClosedOrCompletedJobForClient(clientId) ?: return@withWriteTransaction
                val existingPending = reengagementDao.getPendingEventForClient(clientId)
                if (existingPending != null) {
                    // At most one PENDING event per Client
                    return@withWriteTransaction
                }

                val event = ReengagementEventEntity(
                    id = UUID.randomUUID().toString(),
                    clientId = clientId,
                    jobId = pastJob.id,
                    source = source,
                    occurredAt = System.currentTimeMillis(),
                    status = ReengagementStatus.PENDING
                )
                reengagementDao.insertEvent(event)
            }
        }
    }

    suspend fun resumeJob(eventId: String, jobId: String) {
        var reopenedJob: JobEntity? = null
        val now = System.currentTimeMillis()
        database.withWriteTransaction {
            val current = jobDao.getJobByIdSync(jobId) ?: return@withWriteTransaction
            if (current.status == JobStatus.COMPLETED || current.status == JobStatus.CLOSED) {
                jobDao.updateJobStatus(
                    id = jobId,
                    status = JobStatus.ACTIVE,
                    updatedAt = now,
                    reopenedAt = now
                )
                windowDao.insertWindow(
                    JobAnalysisWindowEntity(
                        jobId = jobId,
                        startedAt = now,
                        reason = WindowReason.REOPENED
                    )
                )
                reopenedJob = jobDao.getJobByIdSync(jobId)
            }
            reengagementDao.updateEventStatus(eventId, ReengagementStatus.RESUMED)
        }
        reopenedJob?.let { scheduler?.scheduleCompletion(it) }
    }

    suspend fun createNewJobFromPrevious(eventId: String, clientId: String, previousJobId: String): String {
        var createdJob: JobEntity? = null
        val newJobId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.withWriteTransaction {
            val prev = jobDao.getJobByIdSync(previousJobId)
            val newJob = JobEntity(
                id = newJobId,
                clientId = clientId,
                serviceId = prev?.serviceId,
                serviceNameSnapshot = prev?.serviceNameSnapshot,
                priceMinor = prev?.priceMinor,
                addressCitySnapshot = prev?.addressCitySnapshot,
                addressDistrictSnapshot = prev?.addressDistrictSnapshot,
                addressStreetSnapshot = prev?.addressStreetSnapshot,
                addressBuildingSnapshot = prev?.addressBuildingSnapshot,
                addressUnitSnapshot = prev?.addressUnitSnapshot,
                addressPostalCodeSnapshot = prev?.addressPostalCodeSnapshot,
                status = JobStatus.ACTIVE,
                createdAt = now,
                updatedAt = now
            )
            jobDao.insertJob(newJob)
            windowDao.insertWindow(
                JobAnalysisWindowEntity(
                    jobId = newJobId,
                    startedAt = now,
                    reason = WindowReason.CREATED
                )
            )
            reengagementDao.updateEventStatus(eventId, ReengagementStatus.NEW_JOB)
            createdJob = newJob
        }
        createdJob?.let { scheduler?.scheduleCompletion(it) }
        return newJobId
    }

    suspend fun ignoreEvent(eventId: String) {
        reengagementDao.updateEventStatus(eventId, ReengagementStatus.IGNORED)
    }
}
