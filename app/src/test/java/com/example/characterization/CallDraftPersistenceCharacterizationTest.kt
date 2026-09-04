package com.example.characterization

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.CallDirection
import com.example.core.model.JobStatus
import com.example.core.model.NameSource
import com.example.core.model.NoteSource
import com.example.core.model.TaskStatus
import com.example.core.model.WindowReason
import com.example.core.phone.PhoneNumberNormalizer
import com.example.data.database.CallUppDatabase
import com.example.data.entity.CallDraftEntity
import com.example.data.entity.ClientEntity
import com.example.data.entity.ServiceEntity
import com.example.data.repository.CallDraftRepository
import com.example.data.repository.OverlayCommitRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Characterization tests for [CallDraftRepository] persistence semantics and [OverlayCommitRequest] flows.
 * Focuses on note-only commit, task generation, auto vs contact client naming,
 * job creation requirements, and call metadata preservation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CallDraftPersistenceCharacterizationTest {

    private lateinit var database: CallUppDatabase
    private lateinit var repository: CallDraftRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CallUppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CallDraftRepository(
            database = database,
            callDraftDao = database.callDraftDao(),
            noteDao = database.noteDao(),
            clientDao = database.clientDao(),
            jobDao = database.jobDao(),
            windowDao = database.jobAnalysisWindowDao(),
            taskDao = database.taskDao(),
            serviceDao = database.serviceDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun commitOverlaySession_noteOnlySave_createsNoteAndDeletesDraftWithoutClientOrJobOrTask() = runBlocking {
        val sessionId = "session-note-only"
        val phone = "+48 501 111 222"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Rozmowa informacyjna o cenniku",
                updatedAt = System.currentTimeMillis()
            )
        )

        val request = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = phone,
            noteText = "Rozmowa informacyjna o cenniku",
            markAsClient = false,
            createJob = false,
            createOpenTask = false
        )

        repository.commitOverlaySession(request)

        val notes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals(1, notes.size)
        assertEquals("Rozmowa informacyjna o cenniku", notes.first().rawText)
        assertEquals(NoteSource.CALL, notes.first().source)

        assertNull(database.callDraftDao().getDraftSync(sessionId))
        assertTrue(repository.isSessionCommitted(sessionId))

        assertNull(database.clientDao().getClientByPhoneKeySync(key))
        assertTrue(database.taskDao().getAllTasksSync().isEmpty())
    }

    @Test
    fun commitOverlaySession_taskRequest_createsOpenTaskLinkedToCreatedNote() = runBlocking {
        val sessionId = "session-task-request"
        val phone = "502333444"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        val request = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = phone,
            noteText = "Oddzwonić w sprawie oferty jutro rano",
            markAsClient = false,
            createJob = false,
            createOpenTask = true
        )

        repository.commitOverlaySession(request)

        val notes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals(1, notes.size)
        val createdNote = notes.first()

        val tasks = database.taskDao().getAllTasksSync()
        assertEquals(1, tasks.size)
        val task = tasks.first()
        assertEquals(TaskStatus.OPEN, task.status)
        assertEquals(createdNote.id, task.noteId)
    }

    @Test
    fun commitOverlaySession_blankNoteWithTaskRequest_createsNeitherNoteNorTask() = runBlocking {
        val sessionId = "session-blank-task-request"
        val phone = "503444555"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        val request = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = phone,
            noteText = "     ",
            markAsClient = false,
            createJob = false,
            createOpenTask = true
        )

        repository.commitOverlaySession(request)

        assertTrue(database.noteDao().getActiveNotesForPhoneSync(key).isEmpty())
        assertTrue(database.taskDao().getAllTasksSync().isEmpty())
        assertTrue(repository.isSessionCommitted(sessionId))
    }

    @Test
    fun commitOverlaySession_markAsClientWithNullDisplayName_generatesAutoDisplayName() = runBlocking {
        val sessionId = "session-auto-client-name"
        val phone = "504555666"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        val request = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = phone,
            noteText = "Klient z polecenia",
            markAsClient = true,
            clientDisplayName = null,
            createJob = false
        )

        repository.commitOverlaySession(request)

        val client = database.clientDao().getClientByPhoneKeySync(key)
        assertNotNull(client)
        assertEquals("Klient +48 504 555 666", client!!.displayName)
        assertEquals(NameSource.AUTO, client.nameSource)
        assertEquals("+48 504 555 666", client.phoneDisplay)
    }

    @Test
    fun commitOverlaySession_markAsClientWithExplicitDisplayName_setsContactNameSource() = runBlocking {
        val sessionId = "session-contact-client-name"
        val phone = "505666777"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        val request = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = phone,
            noteText = "Potwierdzone dane",
            markAsClient = true,
            clientDisplayName = "Adam Mickiewicz",
            createJob = false
        )

        repository.commitOverlaySession(request)

        val client = database.clientDao().getClientByPhoneKeySync(key)
        assertNotNull(client)
        assertEquals("Adam Mickiewicz", client!!.displayName)
        assertEquals(NameSource.CONTACT, client.nameSource)
    }

    @Test
    fun commitOverlaySession_createJobWithoutClient_doesNotCreateJobWhenNoExistingClient() = runBlocking {
        val sessionId = "session-job-no-client"
        val phone = "506777888"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        val request = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = phone,
            noteText = "Chce zlecenie ale brak klienta",
            markAsClient = false,
            createJob = true
        )

        repository.commitOverlaySession(request)

        assertNull(database.clientDao().getClientByPhoneKeySync(key))
        assertEquals(0, database.jobDao().getActiveJobsSync().size)
        assertTrue(repository.isSessionCommitted(sessionId))
    }

    @Test
    fun commitOverlaySession_createJobWithServiceAndClientAddress_snapshotsServiceAndAddressAndOpensWindow() = runBlocking {
        val sessionId = "session-job-service-snapshots"
        val phone = "507888999"
        val key = PhoneNumberNormalizer.normalizeKey(phone)
        val now = System.currentTimeMillis()

        val clientId = UUID.randomUUID().toString()
        val preClient = ClientEntity(
            id = clientId,
            phoneKey = key,
            phoneDisplay = "+48 507 888 999",
            displayName = "Firma Budowlana",
            city = "Gdańsk",
            district = "Wrzeszcz",
            street = "Grunwaldzka",
            buildingNumber = "100",
            unitNumber = "2",
            postalCode = "80-244",
            createdAt = now,
            updatedAt = now
        )
        database.clientDao().insertClient(preClient)

        val serviceId = "service-hydraulik-1"
        database.serviceDao().insertService(
            ServiceEntity(
                id = serviceId,
                name = "Montaż baterii",
                defaultPriceMinor = 25000L
            )
        )

        val request = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = phone,
            noteText = "Zlecenie montażu",
            markAsClient = false,
            createJob = true,
            serviceId = serviceId,
            preliminaryDateEpochDay = 20300L,
            preliminaryTimeMinute = 540
        )

        repository.commitOverlaySession(request)

        val jobs = database.jobDao().getAllJobsForClientSync(clientId)
        assertEquals(1, jobs.size)
        val job = jobs.first()

        assertEquals("Montaż baterii", job.serviceNameSnapshot)
        assertEquals(25000L, job.priceMinor)
        assertEquals("Gdańsk", job.addressCitySnapshot)
        assertEquals("Wrzeszcz", job.addressDistrictSnapshot)
        assertEquals("Grunwaldzka", job.addressStreetSnapshot)
        assertEquals("100", job.addressBuildingSnapshot)
        assertEquals("2", job.addressUnitSnapshot)
        assertEquals("80-244", job.addressPostalCodeSnapshot)
        assertEquals(JobStatus.ACTIVE, job.status)
        assertEquals(20300L, job.preliminaryDateEpochDay)
        assertEquals(540, job.preliminaryTimeMinute)

        val window = database.jobAnalysisWindowDao().getOpenWindowForJob(job.id)
        assertNotNull(window)
        assertEquals(WindowReason.CREATED, window!!.reason)
    }

    @Test
    fun commitOverlaySession_preservesCallDirectionAndCallTimestamp() = runBlocking {
        val sessionId = "session-direction-timestamp"
        val phone = "508999000"
        val key = PhoneNumberNormalizer.normalizeKey(phone)
        val callTime = 1712000000000L

        val request = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = phone,
            noteText = "Kierunek wychodzący",
            markAsClient = false,
            createJob = false,
            callDirection = CallDirection.OUTGOING,
            callTimestamp = callTime
        )

        repository.commitOverlaySession(request)

        val notes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals(1, notes.size)
        val note = notes.first()
        assertEquals(CallDirection.OUTGOING, note.sourceCallDirection)
        assertEquals(callTime, note.sourceCallAt)
    }

    @Test
    fun commitDraftOnCallEnd_preservesCallDirectionAndCallTime() = runBlocking {
        val sessionId = "session-call-end-preserve"
        val phone = "+48509000111"
        val key = PhoneNumberNormalizer.normalizeKey(phone)
        val callTime = 1713000000000L

        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Notatka zapisana automatycznie po zakończeniu połączenia",
                updatedAt = System.currentTimeMillis()
            )
        )

        repository.commitDraftOnCallEnd(
            callSessionId = sessionId,
            callDirection = CallDirection.INCOMING,
            callTime = callTime
        )

        val notes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals(1, notes.size)
        val note = notes.first()
        assertEquals("Notatka zapisana automatycznie po zakończeniu połączenia", note.rawText)
        assertEquals(CallDirection.INCOMING, note.sourceCallDirection)
        assertEquals(callTime, note.sourceCallAt)
        assertNull(database.callDraftDao().getDraftSync(sessionId))
        assertTrue(repository.isSessionCommitted(sessionId))
    }
}