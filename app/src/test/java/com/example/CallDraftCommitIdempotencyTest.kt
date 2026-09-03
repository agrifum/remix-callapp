package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.CallDirection
import com.example.core.phone.PhoneNumberNormalizer
import com.example.data.database.CallUppDatabase
import com.example.data.entity.CallDraftEntity
import com.example.data.repository.CallDraftRepository
import com.example.data.repository.OverlayCommitRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CallDraftCommitIdempotencyTest {

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
    fun `two concurrent IDLE handlers result in exactly one note and draft is deleted`() = runBlocking {
        val sessionId = "session-concurrent-idle"
        val phone = "+48123456789"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        // Seed initial draft
        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Pilna wycena dachu",
                updatedAt = System.currentTimeMillis()
            )
        )

        // Simulate two concurrent IDLE handlers (e.g. PhoneStateReceiver + CallStateMonitor)
        val deferred1 = async(Dispatchers.Default) {
            repository.flushAndCommitOnCallEnd(
                callSessionId = sessionId,
                latestDraft = null,
                callDirection = CallDirection.INCOMING
            )
        }
        val deferred2 = async(Dispatchers.Default) {
            repository.flushAndCommitOnCallEnd(
                callSessionId = sessionId,
                latestDraft = null,
                callDirection = CallDirection.INCOMING
            )
        }

        awaitAll(deferred1, deferred2)

        // Verify only 1 Note was created
        val notes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals(1, notes.size)
        assertEquals("Pilna wycena dachu", notes.first().rawText)

        // Verify draft was deleted
        val draft = database.callDraftDao().getDraftSync(sessionId)
        assertNull(draft)

        // Verify session marked committed
        assertTrue(repository.isSessionCommitted(sessionId))
    }

    @Test
    fun `manual Save followed by IDLE does not create duplicate note`() = runBlocking {
        val sessionId = "session-manual-then-idle"
        val phone = "+48987654321"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        val req = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = key,
            noteText = "Wymiana rur w łazience",
            markAsClient = false,
            createJob = false,
            callDirection = CallDirection.OUTGOING
        )

        // User clicks Save
        repository.commitOverlaySession(req)
        assertTrue(repository.isSessionCommitted(sessionId))

        // Subsequent IDLE event occurs
        repository.flushAndCommitOnCallEnd(
            callSessionId = sessionId,
            latestDraft = CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Wymiana rur w łazience (stale)",
                updatedAt = System.currentTimeMillis()
            ),
            callDirection = CallDirection.OUTGOING
        )

        // Verify exactly one note
        val notes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals(1, notes.size)
        assertEquals("Wymiana rur w łazience", notes.first().rawText)

        // Verify draft is deleted
        val draft = database.callDraftDao().getDraftSync(sessionId)
        assertNull(draft)
    }

    @Test
    fun `call end during debounce flushes latest draft and commits without loss`() = runBlocking {
        val sessionId = "session-debounce-flush"
        val phone = "+48600700800"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        // Seed older draft
        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Wstępna",
                updatedAt = System.currentTimeMillis() - 1000
            )
        )

        // Call ends with latest in-memory draft from overlay
        val latestDraft = CallDraftEntity(
            callSessionId = sessionId,
            phoneKey = key,
            noteText = "Wstępna rozmowa z klientem - potwierdzone",
            updatedAt = System.currentTimeMillis()
        )

        repository.flushAndCommitOnCallEnd(
            callSessionId = sessionId,
            latestDraft = latestDraft,
            callDirection = CallDirection.INCOMING
        )

        // Verify note contains latest text
        val notes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals(1, notes.size)
        assertEquals("Wstępna rozmowa z klientem - potwierdzone", notes.first().rawText)

        // Verify draft is deleted and session committed
        assertNull(database.callDraftDao().getDraftSync(sessionId))
        assertTrue(repository.isSessionCommitted(sessionId))
    }

    @Test
    fun `call end with blank draft creates no note and deletes draft`() = runBlocking {
        val sessionId = "session-blank-note"
        val phone = "+48500500500"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "   ",
                updatedAt = System.currentTimeMillis()
            )
        )

        repository.flushAndCommitOnCallEnd(
            callSessionId = sessionId,
            latestDraft = null,
            callDirection = CallDirection.INCOMING
        )

        val notes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals(0, notes.size)
        assertNull(database.callDraftDao().getDraftSync(sessionId))
        assertTrue(repository.isSessionCommitted(sessionId))
    }

    @Test
    fun `failed transaction does not mark session committed`() = runBlocking {
        val sessionId = "session-failed-tx"
        val phone = "+48511222333"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Tekst notatki",
                updatedAt = System.currentTimeMillis()
            )
        )

        // Close database to force transaction failure
        database.close()

        val result = runCatching {
            repository.flushAndCommitOnCallEnd(
                callSessionId = sessionId,
                latestDraft = null,
                callDirection = CallDirection.INCOMING
            )
        }

        // Must have failed with exception
        assertTrue(result.isFailure)

        // Session must NOT remain marked as committed
        org.junit.Assert.assertFalse(repository.isSessionCommitted(sessionId))
    }

    @Test
    fun `concurrent manual commit and call end IDLE prioritizes manual commit and creates full client job task`() = runBlocking {
        val sessionId = "session-concurrent-manual-idle"
        val phone = "+48777888999"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Draft text",
                updatedAt = System.currentTimeMillis()
            )
        )

        // Manual click registers before auto call-end
        assertTrue(repository.tryClaimManualCommit(sessionId))

        val req = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = key,
            noteText = "Manual overlay note with full processing",
            markAsClient = true,
            clientDisplayName = "Jan Kowalski",
            createJob = true,
            createOpenTask = true,
            callDirection = CallDirection.INCOMING
        )

        val deferredManual = async(Dispatchers.Default) {
            repository.commitOverlaySession(req)
        }
        val deferredIdle = async(Dispatchers.Default) {
            repository.flushAndCommitOnCallEnd(
                callSessionId = sessionId,
                latestDraft = CallDraftEntity(
                    callSessionId = sessionId,
                    phoneKey = key,
                    noteText = "Stale draft text",
                    updatedAt = System.currentTimeMillis()
                ),
                callDirection = CallDirection.INCOMING
            )
        }

        awaitAll(deferredManual, deferredIdle)

        // Verify result matches manual operation
        val notes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals(1, notes.size)
        assertEquals("Manual overlay note with full processing", notes.first().rawText)

        val client = database.clientDao().getClientByPhoneKeySync(key)
        assertNotNull(client)
        assertEquals("Jan Kowalski", client?.displayName)

        val jobs = database.jobDao().getAllJobsForClientSync(client!!.id)
        assertEquals(1, jobs.size)

        val tasks = database.taskDao().getAllTasksSync()
        assertTrue(tasks.isNotEmpty())

        assertNull(database.callDraftDao().getDraftSync(sessionId))
        assertTrue(repository.isSessionCommitted(sessionId))
    }

    @Test
    fun `Test A - manual wins before auto claim`() = runBlocking {
        val sessionId = "session-manual-wins"
        val phone = "+48111222333"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Draft",
                updatedAt = System.currentTimeMillis()
            )
        )

        // 1. State is IDLE_ALLOWED. Manual claims first.
        assertTrue(repository.tryClaimManualCommit(sessionId))
        assertEquals(CallDraftRepository.SessionState.MANUAL_IN_PROGRESS, repository.getSessionState(sessionId))

        // 2. Auto tries claim - must fail
        org.junit.Assert.assertFalse(repository.tryClaimAutoCommit(sessionId))

        // 3. Full manual commit executes
        val req = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = key,
            noteText = "Manual wins note",
            markAsClient = true,
            clientDisplayName = "Anna Nowak",
            createJob = true,
            createOpenTask = true,
            callDirection = CallDirection.INCOMING
        )
        repository.commitOverlaySession(req)

        // Verify result
        val notes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals(1, notes.size)
        assertEquals("Manual wins note", notes.first().rawText)
        assertNotNull(database.clientDao().getClientByPhoneKeySync(key))
        assertEquals(1, database.jobDao().getAllJobsForClientSync(database.clientDao().getClientByPhoneKeySync(key)!!.id).size)
        assertNull(database.callDraftDao().getDraftSync(sessionId))
        assertTrue(repository.isSessionCommitted(sessionId))
    }

    @Test
    fun `Test B - auto wins before manual claim`() = runBlocking {
        val sessionId = "session-auto-wins"
        val phone = "+48444555666"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Auto draft note",
                updatedAt = System.currentTimeMillis()
            )
        )

        // 1. Auto commit executes directly via flushAndCommitOnCallEnd (which claims auto internally)
        repository.flushAndCommitOnCallEnd(
            callSessionId = sessionId,
            latestDraft = null,
            callDirection = CallDirection.OUTGOING
        )

        // Verify result (Note-only commit, no client/job)
        val notes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals(1, notes.size)
        assertEquals("Auto draft note", notes.first().rawText)
        org.junit.Assert.assertNull(database.clientDao().getClientByPhoneKeySync(key))
        assertNull(database.callDraftDao().getDraftSync(sessionId))
        assertTrue(repository.isSessionCommitted(sessionId))
    }

    @Test
    fun `Test C - manual transaction failure resets state to IDLE_ALLOWED`() = runBlocking {
        val sessionId = "session-manual-fail"
        val phone = "+48777666555"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Draft",
                updatedAt = System.currentTimeMillis()
            )
        )

        // Close database to trigger exception
        database.close()

        val req = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = key,
            noteText = "Will fail",
            markAsClient = true,
            createJob = true,
            callDirection = CallDirection.INCOMING
        )

        val result = runCatching {
            repository.commitOverlaySession(req)
        }

        if (!result.isFailure) {
            println("Test C failure: result was success! Exception: ${result.exceptionOrNull()}")
        }
        assertTrue("Expected failure when database is closed", result.isFailure)
        org.junit.Assert.assertFalse(repository.isSessionCommitted(sessionId))
        val state = repository.getSessionState(sessionId)
        println("DEBUG TEST C STATE: $state")
        assertEquals("Expected IDLE_ALLOWED but got $state", CallDraftRepository.SessionState.IDLE_ALLOWED, state)
    }

    @Test
    fun `Test D - auto transaction failure resets state to IDLE_ALLOWED`() = runBlocking {
        val sessionId = "session-auto-fail"
        val phone = "+48888999000"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Draft",
                updatedAt = System.currentTimeMillis()
            )
        )

        database.close()

        val result = runCatching {
            repository.flushAndCommitOnCallEnd(
                callSessionId = sessionId,
                latestDraft = null,
                callDirection = CallDirection.INCOMING
            )
        }

        assertTrue(result.isFailure)
        org.junit.Assert.assertFalse(repository.isSessionCommitted(sessionId))
        assertEquals(CallDraftRepository.SessionState.IDLE_ALLOWED, repository.getSessionState(sessionId))
    }
}
