package com.example.characterization

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.CallDirection
import com.example.core.model.NoteSource
import com.example.core.model.TaskStatus
import com.example.core.phone.PhoneNumberNormalizer
import com.example.data.database.CallUppDatabase
import com.example.data.entity.CallDraftEntity
import com.example.data.repository.CallDraftRepository
import com.example.data.repository.OverlayCommitRequest
import com.example.system.calls.ActiveCallSession
import kotlinx.coroutines.flow.first
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
 * Characterization tests covering MASTER_SPEC §65 mandatory test scenarios:
 * - Call handling (incoming answered, incoming rejected, outgoing answered, outgoing unanswered, rapid end)
 * - Overlay lifecycle (autosave, empty draft cleanup, manual task commit, direction metadata)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CallHandlingAndOverlayCharacterizationTest {

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
        ActiveCallSession.clear()
    }

    @After
    fun tearDown() {
        ActiveCallSession.clear()
        database.close()
    }

    @Test
    fun testIncomingAnsweredWithNoteAutosaveAndEndCommit() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
        val rawNumber = "123 456 789"
        val normalized = PhoneNumberNormalizer.normalizeKey(rawNumber)

        // 1. Telecom screening sets ActiveCallSession
        ActiveCallSession.setCall(rawNumber = rawNumber, direction = CallDirection.INCOMING)
        val activeCall = ActiveCallSession.currentCall.first()
        assertNotNull(activeCall)
        assertEquals(CallDirection.INCOMING, activeCall?.direction)

        // 2. User types note during call (upsert draft)
        val now = System.currentTimeMillis()
        val draft = CallDraftEntity(
            callSessionId = sessionId,
            phoneKey = normalized,
            noteText = "Pilna naprawa piecyka gazowego",
            markAsClient = true,
            createdAt = now,
            updatedAt = now
        )
        database.callDraftDao().upsertDraft(draft)

        val storedDraft = database.callDraftDao().getDraftSync(sessionId)
        assertNotNull(storedDraft)
        assertEquals("Pilna naprawa piecyka gazowego", storedDraft?.noteText)

        // 3. Call ends (flushAndCommitOnCallEnd)
        val startTime = now - 45000L
        repository.flushAndCommitOnCallEnd(
            callSessionId = sessionId,
            latestDraft = storedDraft,
            callDirection = CallDirection.INCOMING,
            callTime = startTime
        )

        // 4. Verify draft is cleared and persistent Note is created
        val draftAfterCommit = database.callDraftDao().getDraftSync(sessionId)
        assertNull(draftAfterCommit)

        val notes = database.noteDao().getActiveNotesForPhoneSync(normalized)
        assertEquals(1, notes.size)
        val note = notes[0]
        assertEquals("Pilna naprawa piecyka gazowego", note.rawText)
        assertEquals(NoteSource.CALL, note.source)
        assertEquals(CallDirection.INCOMING, note.sourceCallDirection)
        assertEquals(startTime, note.sourceCallAt)
    }

    @Test
    fun testIncomingRejectedNeverCreatesGhostRecords() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
        val rawNumber = "+48 500 100 200"
        val normalized = PhoneNumberNormalizer.normalizeKey(rawNumber)

        ActiveCallSession.setCall(rawNumber = rawNumber, direction = CallDirection.INCOMING)

        // Rejected call: user never opened or wrote draft, call ends immediately
        repository.flushAndCommitOnCallEnd(
            callSessionId = sessionId,
            latestDraft = null,
            callDirection = CallDirection.INCOMING,
            callTime = System.currentTimeMillis()
        )

        // Verify zero notes, zero drafts, zero tasks created
        val notes = database.noteDao().getActiveNotesForPhoneSync(normalized)
        assertTrue(notes.isEmpty())
        val draft = database.callDraftDao().getDraftSync(sessionId)
        assertNull(draft)
    }

    @Test
    fun testOutgoingAnsweredWithManualCommitDoZadan() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
        val rawNumber = "601 222 333"
        val normalized = PhoneNumberNormalizer.normalizeKey(rawNumber)

        ActiveCallSession.setCall(rawNumber = rawNumber, direction = CallDirection.OUTGOING)
        assertEquals(CallDirection.OUTGOING, ActiveCallSession.currentCall.first()?.direction)

        // Contractor clicks "Do zadań" during or at end of call
        val commitRequest = OverlayCommitRequest(
            callSessionId = sessionId,
            phone = normalized,
            noteText = "Wymienić zawór 3/4 cala przed weekendem",
            markAsClient = true,
            clientDisplayName = "Jan Kowalski",
            createJob = false,
            createOpenTask = true,
            callDirection = CallDirection.OUTGOING,
            callTimestamp = System.currentTimeMillis()
        )
        repository.commitOverlaySession(commitRequest)

        // Verify Client, Note, and Task were atomically created
        val client = database.clientDao().getClientByPhoneKeySync(normalized)
        assertNotNull(client)
        assertEquals("Jan Kowalski", client?.displayName)

        val notes = database.noteDao().getActiveNotesForPhoneSync(normalized)
        assertEquals(1, notes.size)
        assertEquals(CallDirection.OUTGOING, notes[0].sourceCallDirection)

        val tasks = database.taskDao().getAllTasksSync()
        assertEquals(1, tasks.size)
        assertEquals(notes[0].id, tasks[0].noteId)
        assertEquals(TaskStatus.OPEN, tasks[0].status)
    }

    @Test
    fun testOutgoingUnansweredWithEmptyDraftDiscardsCleanly() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
        val rawNumber = "790 000 111"
        val normalized = PhoneNumberNormalizer.normalizeKey(rawNumber)

        ActiveCallSession.setCall(rawNumber = rawNumber, direction = CallDirection.OUTGOING)

        // Overlay launched, empty draft initialized, but call aborted unanswered
        val now = System.currentTimeMillis()
        val emptyDraft = CallDraftEntity(
            callSessionId = sessionId,
            phoneKey = normalized,
            noteText = "   ",
            createdAt = now,
            updatedAt = now
        )
        database.callDraftDao().upsertDraft(emptyDraft)

        repository.flushAndCommitOnCallEnd(
            callSessionId = sessionId,
            latestDraft = database.callDraftDao().getDraftSync(sessionId),
            callDirection = CallDirection.OUTGOING,
            callTime = now
        )

        // Blank text must be discarded per invariant
        val notes = database.noteDao().getActiveNotesForPhoneSync(normalized)
        assertTrue(notes.isEmpty())
        val draft = database.callDraftDao().getDraftSync(sessionId)
        assertNull(draft)
    }

    @Test
    fun testRapidCallEndHandledGracefully() = runBlocking {
        val sessionId = UUID.randomUUID().toString()
        // Fast sequence: start and end within a few milliseconds
        repository.flushAndCommitOnCallEnd(
            callSessionId = sessionId,
            latestDraft = null,
            callDirection = CallDirection.OUTGOING,
            callTime = System.currentTimeMillis()
        )
        assertTrue(repository.isSessionCommitted(sessionId))
    }
}
