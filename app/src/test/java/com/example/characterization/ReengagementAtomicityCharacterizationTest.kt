package com.example.characterization

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.JobStatus
import com.example.core.model.ReengagementSource
import com.example.core.model.ReengagementStatus
import com.example.data.database.CallUppDatabase
import com.example.data.entity.ClientEntity
import com.example.data.entity.JobEntity
import com.example.data.repository.JobRepository
import com.example.data.repository.ReengagementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReengagementAtomicityCharacterizationTest {

    private lateinit var database: CallUppDatabase
    private lateinit var jobRepository: JobRepository
    private lateinit var reengagementRepository: ReengagementRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CallUppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        jobRepository = JobRepository(
            database = database,
            jobDao = database.jobDao(),
            windowDao = database.jobAnalysisWindowDao()
        )
        reengagementRepository = ReengagementRepository(
            database = database,
            reengagementDao = database.reengagementEventDao(),
            jobDao = database.jobDao(),
            windowDao = database.jobAnalysisWindowDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `two concurrent reengagement attempts for same client - exactly one PENDING event`() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        val phoneKey = "+48501111222"

        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = phoneKey,
                phoneDisplay = "+48 501 111 222",
                displayName = "Klient Reengagement Test"
            )
        )

        // Seed a completed past job
        val pastJobId = UUID.randomUUID().toString()
        database.jobDao().insertJob(
            JobEntity(
                id = pastJobId,
                clientId = clientId,
                status = JobStatus.COMPLETED,
                createdAt = 1000L,
                updatedAt = 2000L
            )
        )

        // Simulate two concurrent incoming SMS reengagement evaluations
        val deferred1 = async(Dispatchers.Default) {
            reengagementRepository.checkAndCreateReengagementEvent(clientId, ReengagementSource.INCOMING_SMS)
        }
        val deferred2 = async(Dispatchers.Default) {
            reengagementRepository.checkAndCreateReengagementEvent(clientId, ReengagementSource.INCOMING_SMS)
        }

        awaitAll(deferred1, deferred2)

        // Verify exactly one PENDING event exists
        val events = reengagementRepository.pendingEvents.first()
        assertEquals("Exactly one PENDING event must exist for the client under concurrency", 1, events.size)
        assertEquals(clientId, events.first().clientId)
        assertEquals(pastJobId, events.first().jobId)
        assertEquals(ReengagementStatus.PENDING, events.first().status)
    }

    @Test
    fun `call + SMS equivalent concurrent attempts - exactly one PENDING event`() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        val phoneKey = "+48502333444"

        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = phoneKey,
                phoneDisplay = "+48 502 333 444",
                displayName = "Klient Call + SMS"
            )
        )

        val pastJobId = UUID.randomUUID().toString()
        database.jobDao().insertJob(
            JobEntity(
                id = pastJobId,
                clientId = clientId,
                status = JobStatus.CLOSED,
                createdAt = 1000L,
                updatedAt = 2000L
            )
        )

        // Race: Call reengagement evaluation vs SMS reengagement evaluation
        val deferredCall = async(Dispatchers.Default) {
            reengagementRepository.checkAndCreateReengagementEvent(clientId, ReengagementSource.INCOMING_CALL)
        }
        val deferredSms = async(Dispatchers.Default) {
            reengagementRepository.checkAndCreateReengagementEvent(clientId, ReengagementSource.INCOMING_SMS)
        }

        awaitAll(deferredCall, deferredSms)

        val events = reengagementRepository.pendingEvents.first()
        assertEquals("Exactly one PENDING event must exist for Call + SMS race", 1, events.size)
        assertEquals(clientId, events.first().clientId)
        assertEquals(pastJobId, events.first().jobId)
        assertEquals(ReengagementStatus.PENDING, events.first().status)
    }

    @Test
    fun `after an earlier PENDING is resolved, a later legitimate reengagement can create a new PENDING event`() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        val phoneKey = "+48503555666"

        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = phoneKey,
                phoneDisplay = "+48 503 555 666",
                displayName = "Klient Re-Resolution"
            )
        )

        val pastJobId = UUID.randomUUID().toString()
        database.jobDao().insertJob(
            JobEntity(
                id = pastJobId,
                clientId = clientId,
                status = JobStatus.COMPLETED,
                createdAt = 1000L,
                updatedAt = 2000L
            )
        )

        // 1. First reengagement creates a PENDING event
        reengagementRepository.checkAndCreateReengagementEvent(clientId, ReengagementSource.INCOMING_CALL)
        val firstEvents = reengagementRepository.pendingEvents.first()
        assertEquals(1, firstEvents.size)
        val eventId = firstEvents.first().id

        // 2. User ignores or resolves the event
        reengagementRepository.ignoreEvent(eventId)

        // Verify pending list is now empty
        val emptyEvents = reengagementRepository.pendingEvents.first()
        assertTrue("Pending events must be empty after resolution", emptyEvents.isEmpty())

        // 3. Later incoming SMS reengagement arrives
        reengagementRepository.checkAndCreateReengagementEvent(clientId, ReengagementSource.INCOMING_SMS)

        // Verify a new PENDING event is created
        val secondEvents = reengagementRepository.pendingEvents.first()
        assertEquals("A new PENDING event must be created after previous event was resolved", 1, secondEvents.size)
        assertEquals(clientId, secondEvents.first().clientId)
        assertEquals(ReengagementSource.INCOMING_SMS, secondEvents.first().source)
        assertEquals(ReengagementStatus.PENDING, secondEvents.first().status)
    }
}
