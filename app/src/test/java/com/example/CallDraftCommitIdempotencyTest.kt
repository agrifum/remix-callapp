package com.example

import android.content.Context
import androidx.room3.Room
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

        // Simulate two concurrent IDLE handlers (e.g. CallOverlayService teardown flush + CallStateMonitor)
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
    fun `auto path begins first, manual Save arrives before final commit - manual intent is not silently lost`() = runBlocking {
        val sessionId = "session-auto-first-manual-upgrades"
        val phone = "+48444555666"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Auto draft note that gets upgraded",
                updatedAt = System.currentTimeMillis()
            )
        )

        // 1. Auto IDLE claims first (AUTO_IN_PROGRESS)
        assertTrue(repository.tryClaimAutoCommit(sessionId))
        assertEquals(CallDraftRepository.SessionState.AUTO_IN_PROGRESS, repository.getSessionState(sessionId))

        // 2. User taps Save before auto finalizes; manual request is registered
        val manualReq = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = key,
            noteText = "Upgraded manual note",
            markAsClient = true,
            clientDisplayName = "Marek Mostowiak",
            createJob = true,
            callDirection = CallDirection.OUTGOING
        )

        // Concurrent execution: auto commit and manual commit race
        val deferredAuto = async(Dispatchers.Default) {
            repository.flushAndCommitOnCallEnd(
                callSessionId = sessionId,
                latestDraft = null,
                callDirection = CallDirection.OUTGOING
            )
        }
        val deferredManual = async(Dispatchers.Default) {
            repository.commitOverlaySession(manualReq)
        }

        awaitAll(deferredAuto, deferredManual)

        // Verify result: manual intent was NOT lost. Client and Job WERE created. Exactly 1 note persisted.
        val notes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals(1, notes.size)
        assertEquals("Upgraded manual note", notes.first().rawText)

        val client = database.clientDao().getClientByPhoneKeySync(key)
        assertNotNull("Client must be created because manual intent superseded auto fallback", client)
        assertEquals("Marek Mostowiak", client?.displayName)

        val jobs = database.jobDao().getAllJobsForClientSync(client!!.id)
        assertEquals(1, jobs.size)

        assertNull(database.callDraftDao().getDraftSync(sessionId))
        assertTrue(repository.isSessionCommitted(sessionId))
    }

    @Test
    fun `auto path begins first, Do zadan arrives before final commit - Task intent is not silently lost`() = runBlocking {
        val sessionId = "session-auto-first-task-upgrades"
        val phone = "+48333444555"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Auto draft note",
                updatedAt = System.currentTimeMillis()
            )
        )

        // Auto claims first
        assertTrue(repository.tryClaimAutoCommit(sessionId))

        val taskReq = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = key,
            noteText = "Zadanie zrobic wycene",
            markAsClient = false,
            createJob = false,
            createOpenTask = true,
            callDirection = CallDirection.INCOMING
        )

        val deferredAuto = async(Dispatchers.Default) {
            repository.flushAndCommitOnCallEnd(
                callSessionId = sessionId,
                latestDraft = null,
                callDirection = CallDirection.INCOMING
            )
        }
        val deferredManual = async(Dispatchers.Default) {
            repository.commitOverlaySession(taskReq)
        }

        awaitAll(deferredAuto, deferredManual)

        // Verify Task was created and linked to Note
        val notes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals(1, notes.size)
        assertEquals("Zadanie zrobic wycene", notes.first().rawText)

        val tasks = database.taskDao().getAllTasksSync()
        assertEquals(1, tasks.size)
        assertEquals(notes.first().id, tasks.first().noteId)
        assertEquals(com.example.core.model.TaskStatus.OPEN, tasks.first().status)

        assertNull(database.callDraftDao().getDraftSync(sessionId))
        assertTrue(repository.isSessionCommitted(sessionId))
    }

    @Test
    fun `auto commit already finalized - late manual request does not create duplicate Note or entities`() = runBlocking {
        val sessionId = "session-auto-finalized-late-manual"
        val phone = "+48222333444"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Initial auto note",
                updatedAt = System.currentTimeMillis()
            )
        )

        // 1. Auto finishes completely and finalizes
        repository.flushAndCommitOnCallEnd(
            callSessionId = sessionId,
            latestDraft = null,
            callDirection = CallDirection.INCOMING
        )

        assertTrue(repository.isSessionCommitted(sessionId))
        val initialNotes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals(1, initialNotes.size)
        assertEquals("Initial auto note", initialNotes.first().rawText)

        // 2. Late manual request arrives after session is already committed
        val lateManualReq = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = key,
            noteText = "Late manual note should be rejected",
            markAsClient = true,
            createJob = true,
            createOpenTask = true,
            callDirection = CallDirection.INCOMING
        )

        repository.commitOverlaySession(lateManualReq)

        // 3. Verify no duplicate note, client, or job was created
        val finalNotes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals("Exactly one note must remain", 1, finalNotes.size)
        assertEquals("Original note must be retained", "Initial auto note", finalNotes.first().rawText)
        assertNull("Late manual request after finalization must not create Client", database.clientDao().getClientByPhoneKeySync(key))
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

        // Close database to deterministically trigger SQLite transaction failure
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

        assertTrue("Expected failure when database is closed", result.isFailure)
        org.junit.Assert.assertFalse(repository.isSessionCommitted(sessionId))
        val state = repository.getSessionState(sessionId)
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

    @Test
    fun `completed session state is eventually removed safely`() = runBlocking {
        val sessionId = "session-cleanup-test"
        val phone = "+48999111222"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Note to commit and cleanup",
                updatedAt = System.currentTimeMillis()
            )
        )

        repository.flushAndCommitOnCallEnd(
            callSessionId = sessionId,
            latestDraft = null,
            callDirection = CallDirection.INCOMING
        )

        assertTrue("Session must be marked committed", repository.isSessionCommitted(sessionId))

        // Perform explicit session release/cleanup
        val wasCommitted = repository.cleanupSession(sessionId)
        assertTrue("cleanupSession must confirm session was committed", wasCommitted)

        // Session must still be recognized as committed via bounded history
        assertTrue("Session must remain committed in bounded history", repository.isSessionCommitted(sessionId))
    }

    @Test
    fun `cleanup does not permit duplicate commit for an already-finalized call during synchronization boundary`() = runBlocking {
        val sessionId = "session-cleanup-idempotency-test"
        val phone = "+48999222333"
        val key = PhoneNumberNormalizer.normalizeKey(phone)

        repository.saveDraft(
            CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Single note",
                updatedAt = System.currentTimeMillis()
            )
        )

        // Commit session
        repository.flushAndCommitOnCallEnd(
            callSessionId = sessionId,
            latestDraft = null,
            callDirection = CallDirection.INCOMING
        )

        assertEquals(1, database.noteDao().getActiveNotesForPhoneSync(key).size)

        // Release session
        repository.cleanupSession(sessionId)

        // Attempt duplicate auto commit with a fresh draft
        repository.flushAndCommitOnCallEnd(
            callSessionId = sessionId,
            latestDraft = CallDraftEntity(
                callSessionId = sessionId,
                phoneKey = key,
                noteText = "Rogue duplicate note",
                updatedAt = System.currentTimeMillis()
            ),
            callDirection = CallDirection.INCOMING
        )

        // Verify still exactly 1 note exists
        val notes = database.noteDao().getActiveNotesForPhoneSync(key)
        assertEquals("Duplicate commit must be prevented even after cleanup", 1, notes.size)
        assertEquals("Single note", notes.first().rawText)
    }
}
