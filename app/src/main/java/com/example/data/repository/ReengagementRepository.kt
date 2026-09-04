package com.example.data.repository

import androidx.room3.withWriteTransaction
import com.example.core.model.JobStatus
import com.example.core.model.ReengagementSource
import com.example.core.model.ReengagementStatus
import com.example.data.database.CallUppDatabase
import com.example.data.dao.JobDao
import com.example.data.dao.ReengagementEventDao
import com.example.data.entity.JobEntity
import com.example.data.entity.ReengagementEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ReengagementRepository(
    private val database: CallUppDatabase,
    private val reengagementDao: ReengagementEventDao,
    private val jobDao: JobDao,
    private val jobRepository: JobRepository
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
        jobRepository.reopenJob(jobId)
        reengagementDao.updateEventStatus(eventId, ReengagementStatus.RESUMED)
    }

    suspend fun createNewJobFromPrevious(eventId: String, clientId: String, previousJobId: String): String {
        val prev = jobDao.getJobByIdSync(previousJobId)
        val newJob = JobEntity(
            id = UUID.randomUUID().toString(),
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
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val newJobId = jobRepository.createJob(newJob, openAnalysisWindow = true)
        reengagementDao.updateEventStatus(eventId, ReengagementStatus.NEW_JOB)
        return newJobId
    }

    suspend fun ignoreEvent(eventId: String) {
        reengagementDao.updateEventStatus(eventId, ReengagementStatus.IGNORED)
    }
}
