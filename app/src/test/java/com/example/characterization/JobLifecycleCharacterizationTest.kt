package com.example.characterization

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.JobStatus
import com.example.core.model.WindowReason
import com.example.data.database.CallUppDatabase
import com.example.data.entity.ClientEntity
import com.example.data.entity.JobEntity
import com.example.data.repository.JobRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Characterization tests for [JobRepository] lifecycle behaviors, duplicate term checks,
 * completion anchor calculations, and status reconciliation threshold rules.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JobLifecycleCharacterizationTest {

    private lateinit var database: CallUppDatabase
    private lateinit var repository: JobRepository
    private lateinit var testClientId: String

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CallUppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = JobRepository(
            database = database,
            jobDao = database.jobDao(),
            windowDao = database.jobAnalysisWindowDao()
        )

        testClientId = UUID.randomUUID().toString()
        database.clientDao().insertClient(
            ClientEntity(
                id = testClientId,
                phoneKey = "+48600111222",
                phoneDisplay = "+48 600 111 222",
                displayName = "Test Klient"
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun createJob_activeJob_opensAnalysisWindowWithReasonCreated() = runBlocking {
        val job = JobEntity(
            clientId = testClientId,
            status = JobStatus.ACTIVE
        )
        val jobId = repository.createJob(job, openAnalysisWindow = true)

        val created = repository.getJobByIdSync(jobId)
        assertNotNull(created)
        assertEquals(JobStatus.ACTIVE, created!!.status)

        val window = database.jobAnalysisWindowDao().getOpenWindowForJob(jobId)
        assertNotNull(window)
        assertEquals(WindowReason.CREATED, window!!.reason)
        assertNull(window.endedAt)
    }

    @Test
    fun createJob_completedJob_doesNotOpenAnalysisWindow() = runBlocking {
        val job = JobEntity(
            clientId = testClientId,
            status = JobStatus.COMPLETED
        )
        val jobId = repository.createJob(job, openAnalysisWindow = true)

        val window = database.jobAnalysisWindowDao().getOpenWindowForJob(jobId)
        assertNull(window)
    }

    @Test
    fun completeJob_setsStatusCompletedAndClosesOpenWindows() = runBlocking {
        val job = JobEntity(
            clientId = testClientId,
            status = JobStatus.ACTIVE
        )
        val jobId = repository.createJob(job)

        repository.completeJob(jobId)

        val updated = repository.getJobByIdSync(jobId)
        assertNotNull(updated)
        assertEquals(JobStatus.COMPLETED, updated!!.status)
        assertNotNull(updated.completedAt)

        val openWindow = database.jobAnalysisWindowDao().getOpenWindowForJob(jobId)
        assertNull(openWindow)
    }

    @Test
    fun closeJob_setsStatusClosedAndClosesOpenWindows() = runBlocking {
        val job = JobEntity(
            clientId = testClientId,
            status = JobStatus.ACTIVE
        )
        val jobId = repository.createJob(job)

        repository.closeJob(jobId)

        val updated = repository.getJobByIdSync(jobId)
        assertNotNull(updated)
        assertEquals(JobStatus.CLOSED, updated!!.status)
        assertNotNull(updated.closedAt)

        assertNull(database.jobAnalysisWindowDao().getOpenWindowForJob(jobId))
    }

    @Test
    fun reopenJob_setsStatusActiveAndInsertsWindowWithReasonReopened() = runBlocking {
        val job = JobEntity(
            clientId = testClientId,
            status = JobStatus.ACTIVE
        )
        val jobId = repository.createJob(job)
        repository.completeJob(jobId)

        repository.reopenJob(jobId)

        val reopened = repository.getJobByIdSync(jobId)
        assertNotNull(reopened)
        assertEquals(JobStatus.ACTIVE, reopened!!.status)
        assertNotNull(reopened.reopenedAt)

        val newWindow = database.jobAnalysisWindowDao().getOpenWindowForJob(jobId)
        assertNotNull(newWindow)
        assertEquals(WindowReason.REOPENED, newWindow!!.reason)
        assertNull(newWindow.endedAt)
    }

    @Test
    fun softDeleteAndRestore_managesAnalysisWindowsAccurately() = runBlocking {
        val job = JobEntity(
            clientId = testClientId,
            status = JobStatus.ACTIVE
        )
        val jobId = repository.createJob(job)

        repository.softDeleteJob(jobId)
        val deleted = repository.getJobByIdSync(jobId)
        assertNotNull(deleted)
        assertNotNull(deleted!!.deletedAt)
        assertNull(database.jobAnalysisWindowDao().getOpenWindowForJob(jobId))

        repository.restoreJob(jobId)
        val restored = repository.getJobByIdSync(jobId)
        assertNotNull(restored)
        assertNull(restored!!.deletedAt)

        val openWindow = database.jobAnalysisWindowDao().getOpenWindowForJob(jobId)
        assertNotNull(openWindow)
        assertEquals(WindowReason.REOPENED, openWindow!!.reason)
    }

    @Test
    fun checkHasDuplicateActiveTerm_matrix() = runBlocking {
        val targetDateEpochDay = 20000L
        val targetTimeMinute = 600

        val existingJob = JobEntity(
            clientId = testClientId,
            status = JobStatus.ACTIVE,
            preliminaryDateEpochDay = targetDateEpochDay,
            preliminaryTimeMinute = targetTimeMinute
        )
        val existingJobId = repository.createJob(existingJob)

        assertTrue(
            repository.checkHasDuplicateActiveTerm(
                clientId = testClientId,
                currentJobId = "new-job-id",
                dateEpochDay = targetDateEpochDay,
                timeMinute = targetTimeMinute
            )
        )

        assertFalse(
            repository.checkHasDuplicateActiveTerm(
                clientId = testClientId,
                currentJobId = existingJobId,
                dateEpochDay = targetDateEpochDay,
                timeMinute = targetTimeMinute
            )
        )

        assertTrue(
            repository.checkHasDuplicateActiveTerm(
                clientId = testClientId,
                currentJobId = "new-job-id",
                dateEpochDay = targetDateEpochDay,
                timeMinute = null
            )
        )

        assertFalse(
            repository.checkHasDuplicateActiveTerm(
                clientId = testClientId,
                currentJobId = "new-job-id",
                dateEpochDay = targetDateEpochDay + 1,
                timeMinute = targetTimeMinute
            )
        )

        assertFalse(
            repository.checkHasDuplicateActiveTerm(
                clientId = testClientId,
                currentJobId = "new-job-id",
                dateEpochDay = null,
                timeMinute = targetTimeMinute
            )
        )

        assertFalse(
            repository.checkHasDuplicateActiveTerm(
                clientId = "other-client-id",
                currentJobId = "new-job-id",
                dateEpochDay = targetDateEpochDay,
                timeMinute = targetTimeMinute
            )
        )
    }

    @Test
    fun calculateCompletionAnchor_prefersConfirmedStartOverPreliminary() {
        val confirmedTime = 1750000000000L
        val job = JobEntity(
            clientId = testClientId,
            confirmedStartAt = confirmedTime,
            preliminaryDateEpochDay = 20000L,
            preliminaryTimeMinute = 720
        )
        val anchor = repository.calculateCompletionAnchor(job)
        assertEquals(confirmedTime, anchor)
    }

    @Test
    fun calculateCompletionAnchor_computesEpochMillisForDateAndTime() {
        val dateEpochDay = 20000L
        val timeMinute = 600
        val job = JobEntity(
            clientId = testClientId,
            confirmedStartAt = null,
            preliminaryDateEpochDay = dateEpochDay,
            preliminaryTimeMinute = timeMinute
        )

        val date = LocalDate.ofEpochDay(dateEpochDay)
        val expectedEpochMilli = LocalDateTime.of(date, LocalTime.of(10, 0))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val anchor = repository.calculateCompletionAnchor(job)
        assertEquals(expectedEpochMilli, anchor)
    }

    @Test
    fun calculateCompletionAnchor_defaultsToEndOfDayWhenTimeMissing() {
        val dateEpochDay = 20000L
        val job = JobEntity(
            clientId = testClientId,
            confirmedStartAt = null,
            preliminaryDateEpochDay = dateEpochDay,
            preliminaryTimeMinute = null
        )

        val date = LocalDate.ofEpochDay(dateEpochDay)
        val expectedEpochMilli = LocalDateTime.of(date, LocalTime.of(23, 59, 59))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val anchor = repository.calculateCompletionAnchor(job)
        assertEquals(expectedEpochMilli, anchor)
    }

    @Test
    fun calculateCompletionAnchor_returnsNullWhenNoDateAvailable() {
        val job = JobEntity(
            clientId = testClientId,
            confirmedStartAt = null,
            preliminaryDateEpochDay = null,
            preliminaryTimeMinute = null
        )
        val anchor = repository.calculateCompletionAnchor(job)
        assertNull(anchor)
    }

    @Test
    fun reconcilerThresholdLogic_characterizesAutoCompletionRule() = runBlocking {
        val now = System.currentTimeMillis()
        val dayInMillis = 24L * 60 * 60 * 1000

        val anchorOlderThan24h = now - (25L * 60 * 60 * 1000)
        val job1 = JobEntity(
            clientId = testClientId,
            status = JobStatus.ACTIVE,
            confirmedStartAt = anchorOlderThan24h
        )
        val job1Id = repository.createJob(job1)

        val anchorUnder24h = now - (20L * 60 * 60 * 1000)
        val job2 = JobEntity(
            clientId = testClientId,
            status = JobStatus.ACTIVE,
            confirmedStartAt = anchorUnder24h
        )
        val job2Id = repository.createJob(job2)

        val activeJobs = repository.getActiveJobsSync()
        for (job in activeJobs) {
            val anchor = repository.calculateCompletionAnchor(job)
            if (anchor != null && (now - anchor) >= dayInMillis) {
                repository.completeJob(job.id)
            }
        }

        assertEquals(JobStatus.COMPLETED, repository.getJobByIdSync(job1Id)!!.status)
        assertEquals(JobStatus.ACTIVE, repository.getJobByIdSync(job2Id)!!.status)
    }
}