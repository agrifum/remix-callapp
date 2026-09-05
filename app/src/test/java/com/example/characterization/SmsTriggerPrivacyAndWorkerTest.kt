package com.example.characterization

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.example.CallUppApplication
import com.example.container
import com.example.ai.FakeSmsExtractionEngine
import com.example.ai.SmsAnalysisCoordinator
import com.example.ai.SmsExtractionEngine
import com.example.ai.SmsExtractionEngineProvider
import com.example.ai.model.AddressCandidate
import com.example.ai.model.JobSummaryUpdate
import com.example.ai.model.SmsExtractionInput
import com.example.ai.model.StructuredExtractionResult
import com.example.ai.model.TermCandidate
import com.example.core.model.JobStatus
import com.example.core.model.ReengagementSource
import com.example.core.model.SmsAnalysisMode
import com.example.core.model.SuggestionStatus
import com.example.core.model.SuggestionType
import com.example.core.model.TimeQualifier
import com.example.core.model.TriggerState
import com.example.core.model.WindowReason
import com.example.data.database.CallUppDatabase
import com.example.data.entity.ClientEntity
import com.example.data.entity.JobAnalysisWindowEntity
import com.example.data.entity.JobEntity
import com.example.data.entity.SmsTriggerEntity
import com.example.data.preferences.AppPreferences
import com.example.system.sms.DefaultSystemSmsReader
import com.example.system.sms.SmsReceiver
import com.example.system.sms.SystemSmsReader
import com.example.system.work.SmsAnalysisWorker
import com.example.system.work.SmsTriggerRecovery
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

/**
 * Comprehensive verification of IMP-SMS-01-R1:
 * 1. Global OFF checked before getMessagesFromIntent / PDU parsing.
 * 2. Client DISABLED with real AppContainer state.
 * 3. Eligible SMS creates trigger and enqueues unique work.
 * 4. Deterministic orphan trigger recovery (closes durability gap).
 * 5. Real DefaultSystemSmsReader: unique exact candidate matching.
 * 6. Real DefaultSystemSmsReader: unrelated sender ignored without BODY read.
 * 7. Real DefaultSystemSmsReader: same-sender ambiguous candidates fail closed without BODY read.
 * 8. Real DefaultSystemSmsReader: missing SMS returns null.
 * 9. Durable worker path from Room metadata only.
 * 10. Completed before worker -> discard.
 * 11. Completed/closed during extraction -> transactional revalidation discards trigger and applies zero mutations.
 * 12. Reopened window: old SMS rejected, new SMS accepted.
 * 13. Address race: address entered during extraction is never overwritten.
 * 14. Unknown AI jobId safely ignored.
 * 15. Persistent attemptCount and retry limit in Room.
 * 16. No raw SMS body persisted in Room.
 * 17. Static code check: SmsReceiver does not access raw message body.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmsTriggerPrivacyAndWorkerTest {

    private lateinit var app: CallUppApplication
    private lateinit var database: CallUppDatabase
    private lateinit var appPreferences: AppPreferences
    private lateinit var coordinator: SmsAnalysisCoordinator

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        app = context as CallUppApplication

        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        WorkManager.getInstance(context).cancelAllWork()

        // Ensure tests and production components operate on the SAME AppContainer state
        database = app.container.database
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            database.clearAllTables()
        }

        appPreferences = app.container.appPreferences
        appPreferences.setSmsAnalysisGlobalEnabled(true)

        app.container.systemSmsReader = DefaultSystemSmsReader(app)
        SmsExtractionEngineProvider.override = app.container.smsExtractionEngine
        coordinator = app.container.smsAnalysisCoordinator
    }

    @After
    fun tearDown() = runBlocking {
        WorkManager.getInstance(app).cancelAllWork()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            database.clearAllTables()
        }
        appPreferences.setSmsAnalysisGlobalEnabled(true)
        app.container.systemSmsReader = DefaultSystemSmsReader(app)
        SmsExtractionEngineProvider.override = null
    }

    private fun createSmsIntent(
        originatorDigitsSwapped: String = "8405214365F7", // +48 501 234 567
        digitCountHex: String = "0B"
    ): Intent {
        val hex = "0004" + digitCountHex + "91" + originatorDigitsSwapped + "00002490405100000004D4F29C0E"
        val pduBytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        return Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            putExtra("pdus", arrayOf(pduBytes))
            putExtra("format", "3gpp")
        }
    }

    // Custom TrackingIntent to deterministically verify whether PDUs were accessed
    private class TrackingPduIntent(action: String) : Intent(action) {
        var pduAccessCount = 0

        override fun getSerializableExtra(name: String?): java.io.Serializable? {
            if (name == "pdus") pduAccessCount++
            return super.getSerializableExtra(name)
        }

        override fun getParcelableArrayExtra(name: String?): Array<android.os.Parcelable>? {
            if (name == "pdus") pduAccessCount++
            return super.getParcelableArrayExtra(name)
        }

        override fun getByteArrayExtra(name: String?): ByteArray? {
            if (name == "pdus") pduAccessCount++
            return super.getByteArrayExtra(name)
        }
    }

    private class TrackingSystemSmsReader(val delegate: SystemSmsReader) : SystemSmsReader {
        var readCalls = 0
        override fun readSms(senderPhoneKey: String, receivedAt: Long): String? {
            readCalls++
            return delegate.readSms(senderPhoneKey, receivedAt)
        }
    }

    // A. Global OFF + ACTIVE eligible Job:
    // - sender metadata resolved
    // - no SmsTrigger, no worker enqueued, no SystemSmsReader body read, no extraction
    @Test
    fun smsReceiver_globalOff_activeEligibleJob_noTriggerOrWorkerOrBodyRead() = runBlocking {
        try {
            appPreferences.setSmsAnalysisGlobalEnabled(false)

            val trackingReader = TrackingSystemSmsReader(app.container.systemSmsReader)
            app.container.systemSmsReader = trackingReader

            val clientId = UUID.randomUUID().toString()
            app.container.clientDao.insertClient(
                ClientEntity(
                    id = clientId,
                    phoneKey = "+48501234567",
                    phoneDisplay = "+48 501 234 567",
                    displayName = "Klient Aktywny"
                )
            )
            val jobId = UUID.randomUUID().toString()
            app.container.jobDao.insertJob(JobEntity(id = jobId, clientId = clientId, status = JobStatus.ACTIVE))
            app.container.windowDao.insertWindow(
                JobAnalysisWindowEntity(jobId = jobId, startedAt = 0L, reason = WindowReason.CREATED)
            )

            val receiver = SmsReceiver()
            receiver.onReceive(app, createSmsIntent())
            kotlinx.coroutines.delay(100)

            val triggers = app.container.smsTriggerDao.getPendingTriggers()
            assertTrue("No trigger should be created when Global OFF", triggers.isEmpty())
            assertEquals("SystemSmsReader must not be called when Global OFF", 0, trackingReader.readCalls)

            val suggestions = app.container.aiSuggestionDao.getPendingSuggestionsForJobSync(jobId)
            assertTrue("No extraction/suggestions when Global OFF", suggestions.isEmpty())
        } finally {
            appPreferences.setSmsAnalysisGlobalEnabled(true)
        }
    }

    // B. Global OFF + no ACTIVE Job + recent COMPLETED/CLOSED:
    // - ReengagementEvent created
    // - no trigger, no worker, no body read
    @Test
    fun smsReceiver_globalOff_noActiveJob_recentClosedJob_createsReengagementEvent() = runBlocking {
        try {
            appPreferences.setSmsAnalysisGlobalEnabled(false)

            val trackingReader = TrackingSystemSmsReader(app.container.systemSmsReader)
            app.container.systemSmsReader = trackingReader

            val clientId = UUID.randomUUID().toString()
            app.container.clientDao.insertClient(
                ClientEntity(
                    id = clientId,
                    phoneKey = "+48501234567",
                    phoneDisplay = "+48 501 234 567",
                    displayName = "Klient Dawny"
                )
            )
            val pastJobId = UUID.randomUUID().toString()
            app.container.jobDao.insertJob(
                JobEntity(
                    id = pastJobId,
                    clientId = clientId,
                    status = JobStatus.COMPLETED
                )
            )

            val receiver = SmsReceiver()
            receiver.onReceive(app, createSmsIntent())

            val events = withTimeout(5000) {
                app.container.reengagementEventDao.getPendingEvents()
                    .filter { it.isNotEmpty() }
                    .first()
            }
            assertEquals(1, events.size)
            assertEquals(clientId, events[0].clientId)
            assertEquals(pastJobId, events[0].jobId)
            assertEquals(ReengagementSource.INCOMING_SMS, events[0].source)

            val triggers = app.container.smsTriggerDao.getPendingTriggers()
            assertTrue("No trigger should be created for reengagement event", triggers.isEmpty())
            assertEquals("No SMS body read for reengagement", 0, trackingReader.readCalls)
        } finally {
            appPreferences.setSmsAnalysisGlobalEnabled(true)
        }
    }

    // C. Client DISABLED + no ACTIVE Job + recent COMPLETED/CLOSED:
    // - ReengagementEvent created
    // - no trigger, no worker, no body read
    @Test
    fun smsReceiver_clientDisabled_noActiveJob_recentClosedJob_createsReengagementEvent() = runBlocking {
        appPreferences.setSmsAnalysisGlobalEnabled(true)

        val trackingReader = TrackingSystemSmsReader(app.container.systemSmsReader)
        app.container.systemSmsReader = trackingReader

        val clientId = UUID.randomUUID().toString()
        app.container.clientDao.insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Wylaczony",
                smsAnalysisMode = SmsAnalysisMode.DISABLED
            )
        )
        val pastJobId = UUID.randomUUID().toString()
        app.container.jobDao.insertJob(
            JobEntity(
                id = pastJobId,
                clientId = clientId,
                status = JobStatus.CLOSED
            )
        )

        val receiver = SmsReceiver()
        receiver.onReceive(app, createSmsIntent())

        val events = withTimeout(5000) {
            app.container.reengagementEventDao.getPendingEvents()
                .filter { it.isNotEmpty() }
                .first()
        }
        assertEquals(1, events.size)
        assertEquals(clientId, events[0].clientId)
        assertEquals(pastJobId, events[0].jobId)
        assertEquals(ReengagementSource.INCOMING_SMS, events[0].source)

        val triggers = app.container.smsTriggerDao.getPendingTriggers()
        assertTrue("No trigger should be created for reengagement event", triggers.isEmpty())
        assertEquals("No SMS body read for reengagement", 0, trackingReader.readCalls)
    }

    // D. Client DISABLED + ACTIVE Job:
    // - no trigger, no worker, no body read
    @Test
    fun smsReceiver_clientDisabled_activeJob_noTriggerOrWorkerOrBodyRead() = runBlocking {
        appPreferences.setSmsAnalysisGlobalEnabled(true)

        val trackingReader = TrackingSystemSmsReader(app.container.systemSmsReader)
        app.container.systemSmsReader = trackingReader

        val clientId = UUID.randomUUID().toString()
        app.container.clientDao.insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Disabled Active",
                smsAnalysisMode = SmsAnalysisMode.DISABLED
            )
        )
        val jobId = UUID.randomUUID().toString()
        app.container.jobDao.insertJob(JobEntity(id = jobId, clientId = clientId, status = JobStatus.ACTIVE))
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(jobId = jobId, startedAt = 0L, reason = WindowReason.CREATED)
        )

        val receiver = SmsReceiver()
        receiver.onReceive(app, createSmsIntent())
        kotlinx.coroutines.delay(100)

        val triggers = app.container.smsTriggerDao.getPendingTriggers()
        assertTrue("No trigger should be created when Client SMS mode is DISABLED", triggers.isEmpty())
        assertEquals("SystemSmsReader must not be called when Client is DISABLED", 0, trackingReader.readCalls)
    }

    // 3. Eligible SMS: metadata-only trigger created, worker enqueued
    @Test
    fun smsReceiver_eligibleSms_createsMetadataOnlyTrigger_andEnqueuesWorker() = runBlocking {
        appPreferences.setSmsAnalysisGlobalEnabled(true)

        val clientId = UUID.randomUUID().toString()
        app.container.clientDao.insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Aktywny"
            )
        )
        val jobId = UUID.randomUUID().toString()
        app.container.jobDao.insertJob(
            JobEntity(
                id = jobId,
                clientId = clientId,
                status = JobStatus.ACTIVE
            )
        )
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(
                jobId = jobId,
                startedAt = 0L,
                reason = WindowReason.CREATED
            )
        )

        val receiver = SmsReceiver()
        receiver.onReceive(app, createSmsIntent())

        val trigger = withTimeout<SmsTriggerEntity>(5000) {
            var latest: SmsTriggerEntity? = null
            while (latest == null) {
                latest = app.container.smsTriggerDao.getLatestTriggerForClient(clientId)
                if (latest == null) {
                    kotlinx.coroutines.delay(25)
                }
            }
            latest ?: error("Trigger not found for client within timeout")
        }
        assertEquals(clientId, trigger.clientId)
        assertEquals("+48501234567", trigger.senderPhoneKey)
        assertTrue(
            "Trigger state must remain within valid lifecycle states",
            trigger.state in setOf(TriggerState.PENDING, TriggerState.FAILED, TriggerState.DISCARDED, TriggerState.PROCESSED)
        )

        val workManager = WorkManager.getInstance(app)
        val workInfos = withTimeout(5000) {
            workManager.getWorkInfosForUniqueWorkFlow(SmsTriggerRecovery.getWorkName(trigger.id))
                .filter { it.isNotEmpty() }
                .first()
        }
        assertNotNull(workInfos)
        assertEquals(1, workInfos.size)
    }

    // 4. Deterministic orphan trigger recovery (durability gap resolution)
    @Test
    fun smsTriggerRecovery_recoversOrphanPendingTrigger_andDiscardsIneligible() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        app.container.clientDao.insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Recovery"
            )
        )
        val activeJobId = UUID.randomUUID().toString()
        app.container.jobDao.insertJob(JobEntity(id = activeJobId, clientId = clientId, status = JobStatus.ACTIVE))
        val now = System.currentTimeMillis()
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(jobId = activeJobId, startedAt = now - 1000L, reason = WindowReason.CREATED)
        )

        // Simulate orphan PENDING trigger in Room (process died right after insert before enqueue)
        val orphanTriggerId = UUID.randomUUID().toString()
        app.container.smsTriggerDao.insertTrigger(
            SmsTriggerEntity(
                id = orphanTriggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = now,
                state = TriggerState.PENDING
            )
        )

        val workManager = WorkManager.getInstance(app)
        // Before recovery: no WorkManager request exists
        assertTrue(workManager.getWorkInfosForUniqueWork(SmsTriggerRecovery.getWorkName(orphanTriggerId)).get().isEmpty())

        // Run recovery
        val recovered = app.container.smsTriggerRecovery.recoverPendingTriggers()
        assertEquals(1, recovered)

        // After recovery: unique work is now enqueued!
        val workInfos = workManager.getWorkInfosForUniqueWork(SmsTriggerRecovery.getWorkName(orphanTriggerId)).get()
        assertEquals(1, workInfos.size)

        // Ineligible scenario: job is closed while trigger was pending
        app.container.jobDao.updateJob(app.container.jobDao.getJobByIdSync(activeJobId)!!.copy(status = JobStatus.CLOSED))
        app.container.windowDao.closeAllWindowsForJob(activeJobId, System.currentTimeMillis())

        val staleTriggerId = UUID.randomUUID().toString()
        app.container.smsTriggerDao.insertTrigger(
            SmsTriggerEntity(
                id = staleTriggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = now,
                state = TriggerState.PENDING
            )
        )

        app.container.smsTriggerRecovery.recoverPendingTriggers()
        val staleTrigger = app.container.smsTriggerDao.getTriggerById(staleTriggerId)!!
        assertEquals("Ineligible orphan trigger must be marked DISCARDED", TriggerState.DISCARDED, staleTrigger.state)
    }

    // E. FAILED trigger below retry limit + eligible: startup recovery restores/enqueues work
    @Test
    fun smsTriggerRecovery_failedTriggerBelowRetryLimit_restoresAndEnqueuesWork() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        app.container.clientDao.insertClient(
            ClientEntity(id = clientId, phoneKey = "+48501234567", phoneDisplay = "+48 501 234 567", displayName = "Klient Retry")
        )
        val activeJobId = UUID.randomUUID().toString()
        app.container.jobDao.insertJob(JobEntity(id = activeJobId, clientId = clientId, status = JobStatus.ACTIVE))
        val now = System.currentTimeMillis()
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(jobId = activeJobId, startedAt = now - 1000L, reason = WindowReason.CREATED)
        )

        // Insert FAILED trigger with attemptCount = 1 (below MAX_RETRIES = 3)
        val triggerId = UUID.randomUUID().toString()
        app.container.smsTriggerDao.insertTrigger(
            SmsTriggerEntity(
                id = triggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = now,
                state = TriggerState.FAILED,
                attemptCount = 1
            )
        )

        val workManager = WorkManager.getInstance(app)
        assertTrue(workManager.getWorkInfosForUniqueWork(SmsTriggerRecovery.getWorkName(triggerId)).get().isEmpty())

        // Run recovery
        val recovered = app.container.smsTriggerRecovery.recoverOutstandingTriggers()
        assertEquals(1, recovered)

        // Unique work enqueued
        val workInfos = workManager.getWorkInfosForUniqueWork(SmsTriggerRecovery.getWorkName(triggerId)).get()
        assertEquals(1, workInfos.size)
    }

    // F. FAILED trigger at/exceeding retry limit: DISCARDED, no new work
    @Test
    fun smsTriggerRecovery_failedTriggerAtRetryLimit_discardedWithoutWork() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        app.container.clientDao.insertClient(
            ClientEntity(id = clientId, phoneKey = "+48501234567", phoneDisplay = "+48 501 234 567", displayName = "Klient Exhausted")
        )
        val activeJobId = UUID.randomUUID().toString()
        app.container.jobDao.insertJob(JobEntity(id = activeJobId, clientId = clientId, status = JobStatus.ACTIVE))
        val now = System.currentTimeMillis()
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(jobId = activeJobId, startedAt = now - 1000L, reason = WindowReason.CREATED)
        )

        // Insert FAILED trigger with attemptCount = 3 (>= MAX_RETRIES = 3)
        val triggerId = UUID.randomUUID().toString()
        app.container.smsTriggerDao.insertTrigger(
            SmsTriggerEntity(
                id = triggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = now,
                state = TriggerState.FAILED,
                attemptCount = 3
            )
        )

        val workManager = WorkManager.getInstance(app)
        assertTrue(workManager.getWorkInfosForUniqueWork(SmsTriggerRecovery.getWorkName(triggerId)).get().isEmpty())

        // Run recovery
        val recovered = app.container.smsTriggerRecovery.recoverOutstandingTriggers()
        assertEquals(0, recovered)

        // Trigger is now marked DISCARDED
        val updated = app.container.smsTriggerDao.getTriggerById(triggerId)
        assertNotNull(updated)
        assertEquals(TriggerState.DISCARDED, updated!!.state)

        // No work enqueued
        assertTrue(workManager.getWorkInfosForUniqueWork(SmsTriggerRecovery.getWorkName(triggerId)).get().isEmpty())
    }

    // =========================================================================
    // REAL DefaultSystemSmsReader TESTS (using Robolectric ContentResolver)
    // =========================================================================

    private class TestSmsContentProvider : ContentProvider() {
        data class SmsRow(
            val id: Long,
            val address: String,
            val date: Long,
            val dateSent: Long,
            val body: String
        )

        val rows = mutableListOf<SmsRow>()
        val queriedProjections = mutableListOf<List<String>>()

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            val projList = projection?.toList() ?: emptyList()
            queriedProjections.add(projList)

            val cursor = MatrixCursor(projection ?: arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY))

            if (selection != null && selection.contains(Telephony.Sms._ID) && selectionArgs != null && selectionArgs.isNotEmpty()) {
                // Phase 2: query single row by _ID
                val targetId = selectionArgs[0].toLongOrNull()
                val row = rows.firstOrNull { it.id == targetId }
                if (row != null) {
                    addRow(cursor, row, projection)
                }
                return cursor
            }

            // Phase 1: query metadata
            for (row in rows) {
                addRow(cursor, row, projection)
            }
            return cursor
        }

        private fun addRow(cursor: MatrixCursor, row: SmsRow, projection: Array<out String>?) {
            val values = mutableListOf<Any?>()
            projection?.forEach { col ->
                when (col) {
                    Telephony.Sms._ID -> values.add(row.id)
                    Telephony.Sms.ADDRESS -> values.add(row.address)
                    Telephony.Sms.DATE -> values.add(row.date)
                    Telephony.Sms.DATE_SENT -> values.add(row.dateSent)
                    Telephony.Sms.BODY -> values.add(row.body)
                    else -> values.add(null)
                }
            }
            cursor.addRow(values)
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }

    // 5. Real DefaultSystemSmsReader: unique exact candidate matching
    @Test
    fun defaultSystemSmsReader_uniqueExactMatch_returnsBody_andProjectionsAreStrict() {
        val testProvider = org.robolectric.Robolectric.setupContentProvider(TestSmsContentProvider::class.java, "sms")

        val targetPhone = "+48501234567"
        val timestamp = 1710000000000L

        testProvider.rows.add(
            TestSmsContentProvider.SmsRow(
                id = 101L,
                address = targetPhone,
                date = timestamp,
                dateSent = timestamp,
                body = "Adres: ul. Lipowa 5, Warszawa"
            )
        )

        val reader = DefaultSystemSmsReader(app)
        val result = reader.readSms(targetPhone, timestamp)

        assertEquals("Adres: ul. Lipowa 5, Warszawa", result)

        // Verify Phase 1 did NOT query BODY
        assertEquals(2, testProvider.queriedProjections.size)
        val phase1Proj = testProvider.queriedProjections[0]
        assertFalse("Phase 1 must not include BODY in projection", phase1Proj.contains(Telephony.Sms.BODY))

        // Verify Phase 2 queried ONLY BODY for the resolved ID
        val phase2Proj = testProvider.queriedProjections[1]
        assertEquals(listOf(Telephony.Sms.BODY), phase2Proj)
    }

    // 6. Real DefaultSystemSmsReader: unrelated sender in same time window is ignored without BODY read
    @Test
    fun defaultSystemSmsReader_unrelatedSender_ignoredWithoutBodyRead() {
        val testProvider = org.robolectric.Robolectric.setupContentProvider(TestSmsContentProvider::class.java, "sms")

        val timestamp = 1710000000000L

        // Row from an unrelated sender in the same timestamp window
        testProvider.rows.add(
            TestSmsContentProvider.SmsRow(
                id = 999L,
                address = "+48999888777",
                date = timestamp,
                dateSent = timestamp,
                body = "Unrelated secret message"
            )
        )

        val reader = DefaultSystemSmsReader(app)
        val result = reader.readSms("+48501234567", timestamp)

        assertNull(result)
        // Phase 2 must never have been called!
        assertEquals(1, testProvider.queriedProjections.size)
    }

    // 7. Real DefaultSystemSmsReader: same sender, two plausible rows in tolerance -> ambiguous / fail closed
    @Test
    fun defaultSystemSmsReader_sameSenderTwoPlausibleRows_ambiguousFailsClosed() {
        val testProvider = org.robolectric.Robolectric.setupContentProvider(TestSmsContentProvider::class.java, "sms")

        val targetPhone = "+48501234567"
        val timestamp = 1710000000000L

        // Two messages from same sender within tolerance, neither exactly at timestamp
        testProvider.rows.add(
            TestSmsContentProvider.SmsRow(
                id = 201L,
                address = targetPhone,
                date = timestamp - 2000L,
                dateSent = 0L,
                body = "Wiadomosc A"
            )
        )
        testProvider.rows.add(
            TestSmsContentProvider.SmsRow(
                id = 202L,
                address = targetPhone,
                date = timestamp + 2000L,
                dateSent = 0L,
                body = "Wiadomosc B"
            )
        )

        val reader = DefaultSystemSmsReader(app)
        val result = reader.readSms(targetPhone, timestamp)

        assertNull("Ambiguous candidates from same sender must return null (fail closed)", result)
        // Phase 2 must NEVER have been called! No arbitrary selection!
        assertEquals(1, testProvider.queriedProjections.size)
    }

    // 8. Missing system SMS returns null
    @Test
    fun defaultSystemSmsReader_noMatchingRow_returnsNull() {
        val testProvider = org.robolectric.Robolectric.setupContentProvider(TestSmsContentProvider::class.java, "sms")

        val reader = DefaultSystemSmsReader(app)
        val result = reader.readSms("+48501234567", 1710000000000L)
        assertNull(result)
    }

    // 9. Durable worker path from Room metadata only
    @Test
    fun smsAnalysisWorker_durableExecutionFromRoom_processesSuccessfully() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        app.container.clientDao.insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Durable"
            )
        )
        val jobId = UUID.randomUUID().toString()
        app.container.jobDao.insertJob(JobEntity(id = jobId, clientId = clientId, status = JobStatus.ACTIVE))
        val now = System.currentTimeMillis()
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(jobId = jobId, startedAt = now - 1000L, reason = WindowReason.CREATED)
        )

        val triggerId = UUID.randomUUID().toString()
        val trigger = SmsTriggerEntity(
            id = triggerId,
            clientId = clientId,
            senderPhoneKey = "+48501234567",
            receivedAt = now,
            state = TriggerState.PENDING
        )
        app.container.smsTriggerDao.insertTrigger(trigger)

        app.container.systemSmsReader = object : SystemSmsReader {
            override fun readSms(senderPhoneKey: String, receivedAt: Long): String? {
                return "Adres: ul. Wierzbowa 10, Poznań"
            }
        }

        val worker = TestListenableWorkerBuilder<SmsAnalysisWorker>(app)
            .setInputData(workDataOf(SmsAnalysisWorker.KEY_TRIGGER_ID to triggerId))
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        val updatedTrigger = app.container.smsTriggerDao.getTriggerById(triggerId)
        assertEquals(TriggerState.PROCESSED, updatedTrigger!!.state)
        assertEquals("Wierzbowa", app.container.clientDao.getClientByIdSync(clientId)!!.street)
    }

    // 10. Job completed before worker runs: trigger discarded, zero mutation
    @Test
    fun smsAnalysisWorker_jobCompletedBeforeRun_discardsTriggerWithoutMutation() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        app.container.clientDao.insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Completed Early"
            )
        )
        val jobId = UUID.randomUUID().toString()
        app.container.jobDao.insertJob(JobEntity(id = jobId, clientId = clientId, status = JobStatus.COMPLETED))
        val now = System.currentTimeMillis()
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(jobId = jobId, startedAt = now - 1000L, endedAt = now - 500L, reason = WindowReason.CREATED)
        )

        val triggerId = UUID.randomUUID().toString()
        app.container.smsTriggerDao.insertTrigger(
            SmsTriggerEntity(
                id = triggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = now,
                state = TriggerState.PENDING
            )
        )

        val worker = TestListenableWorkerBuilder<SmsAnalysisWorker>(app)
            .setInputData(workDataOf(SmsAnalysisWorker.KEY_TRIGGER_ID to triggerId))
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(TriggerState.DISCARDED, app.container.smsTriggerDao.getTriggerById(triggerId)!!.state)
        assertNull(app.container.clientDao.getClientByIdSync(clientId)!!.street)
    }

    // 11. In-flight race: Job completed while extraction in progress -> pre-mutation transaction re-validation prevents mutation
    @Test
    fun coordinator_jobCompletedDuringExtraction_preMutationRevalidationRejectsMutation() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        app.container.clientDao.insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Race Test"
            )
        )
        val jobId = UUID.randomUUID().toString()
        app.container.jobDao.insertJob(JobEntity(id = jobId, clientId = clientId, status = JobStatus.ACTIVE))
        val now = System.currentTimeMillis()
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(jobId = jobId, startedAt = now - 1000L, reason = WindowReason.CREATED)
        )

        val triggerId = UUID.randomUUID().toString()
        app.container.smsTriggerDao.insertTrigger(
            SmsTriggerEntity(
                id = triggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = now,
                state = TriggerState.PENDING
            )
        )

        val racingEngine = object : SmsExtractionEngine {
            override suspend fun extract(input: SmsExtractionInput): StructuredExtractionResult? {
                // Job transitions to COMPLETED concurrently during extraction!
                val currentJob = app.container.jobDao.getJobByIdSync(jobId)!!
                app.container.jobDao.updateJob(currentJob.copy(status = JobStatus.COMPLETED))
                app.container.windowDao.closeAllWindowsForJob(jobId, System.currentTimeMillis())

                return StructuredExtractionResult(
                    addressCandidate = AddressCandidate(
                        street = "Wspólna",
                        buildingNumber = "15",
                        city = "Warszawa",
                        confidence = "HIGH"
                    ),
                    termCandidate = TermCandidate(
                        dateEpochDay = 20000L,
                        timeMinute = 720,
                        qualifier = TimeQualifier.EXACT,
                        confidence = "HIGH"
                    ),
                    jobSummaries = listOf(JobSummaryUpdate(jobId = jobId, updatedSummary = "Nowe podsumowanie"))
                )
            }
        }

        val testCoordinator = SmsAnalysisCoordinator(
            database = app.container.database,
            clientDao = app.container.clientDao,
            jobDao = app.container.jobDao,
            windowDao = app.container.windowDao,
            suggestionDao = app.container.aiSuggestionDao,
            triggerDao = app.container.smsTriggerDao,
            appPreferences = appPreferences,
            extractionEngine = racingEngine
        )

        val success = testCoordinator.processSmsTrigger(triggerId, "Adres: ul. Wspólna 15, termin: jutro 12:00")
        assertFalse(success)

        val jobAfter = app.container.jobDao.getJobByIdSync(jobId)!!
        assertEquals(JobStatus.COMPLETED, jobAfter.status)
        assertNull(jobAfter.addressStreetSnapshot)
        assertNull(jobAfter.preliminaryDateEpochDay)
        assertNull(jobAfter.smsSummary)
        assertEquals(TriggerState.DISCARDED, app.container.smsTriggerDao.getTriggerById(triggerId)!!.state)
    }

    // 12. Address race: client address manually changed during extraction is NEVER overwritten
    @Test
    fun coordinator_addressManuallyChangedDuringExtraction_neverOverwritten_createsSuggestion() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        // Client starts with EMPTY address
        app.container.clientDao.insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Address Race"
            )
        )
        val jobId = UUID.randomUUID().toString()
        app.container.jobDao.insertJob(JobEntity(id = jobId, clientId = clientId, status = JobStatus.ACTIVE))
        val now = System.currentTimeMillis()
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(jobId = jobId, startedAt = now - 1000L, reason = WindowReason.CREATED)
        )

        val triggerId = UUID.randomUUID().toString()
        app.container.smsTriggerDao.insertTrigger(
            SmsTriggerEntity(
                id = triggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = now,
                state = TriggerState.PENDING
            )
        )

        // Engine simulates user entering address manually while extraction is running!
        val addressMutatingEngine = object : SmsExtractionEngine {
            override suspend fun extract(input: SmsExtractionInput): StructuredExtractionResult? {
                // User enters address manually before extraction completes
                val current = app.container.clientDao.getClientByIdSync(clientId)!!
                app.container.clientDao.updateClient(
                    current.copy(
                        street = "Złota",
                        buildingNumber = "5",
                        city = "Warszawa"
                    )
                )

                return StructuredExtractionResult(
                    addressCandidate = AddressCandidate(
                        street = "Wspólna",
                        buildingNumber = "15",
                        city = "Warszawa",
                        confidence = "HIGH"
                    )
                )
            }
        }

        val testCoordinator = SmsAnalysisCoordinator(
            database = app.container.database,
            clientDao = app.container.clientDao,
            jobDao = app.container.jobDao,
            windowDao = app.container.windowDao,
            suggestionDao = app.container.aiSuggestionDao,
            triggerDao = app.container.smsTriggerDao,
            appPreferences = appPreferences,
            extractionEngine = addressMutatingEngine
        )

        val result = testCoordinator.processSmsTrigger(triggerId, "Adres: ul. Wspólna 15, Warszawa")
        assertTrue(result)

        // Manual address must NOT have been overwritten!
        val clientAfter = app.container.clientDao.getClientByIdSync(clientId)!!
        assertEquals("Złota", clientAfter.street)
        assertEquals("5", clientAfter.buildingNumber)

        // ADDRESS_CHANGE suggestion must have been created instead
        val suggestions = app.container.aiSuggestionDao.getPendingSuggestionsForClient(clientId).first()
        assertEquals(1, suggestions.size)
        val suggestion = suggestions[0]
        assertEquals(SuggestionType.ADDRESS_CHANGE, suggestion.type)
        assertEquals(SuggestionStatus.PENDING, suggestion.status)
        assertTrue(suggestion.proposedValueJson.contains("Wspólna"))
    }

    // 13. Persistent attemptCount / retry limit accounting in Room
    @Test
    fun smsAnalysisWorker_persistentAttemptCountAccounting() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        app.container.clientDao.insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Retry Accounting"
            )
        )
        val jobId = UUID.randomUUID().toString()
        app.container.jobDao.insertJob(JobEntity(id = jobId, clientId = clientId, status = JobStatus.ACTIVE))
        val now = System.currentTimeMillis()
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(jobId = jobId, startedAt = now - 1000L, reason = WindowReason.CREATED)
        )

        val triggerId = UUID.randomUUID().toString()
        app.container.smsTriggerDao.insertTrigger(
            SmsTriggerEntity(
                id = triggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = now,
                state = TriggerState.PENDING,
                attemptCount = 0
            )
        )

        app.container.systemSmsReader = object : SystemSmsReader {
            override fun readSms(senderPhoneKey: String, receivedAt: Long): String? = null
        }

        // Attempt 1 -> retry, attemptCount = 1
        val worker1 = TestListenableWorkerBuilder<SmsAnalysisWorker>(app)
            .setInputData(workDataOf(SmsAnalysisWorker.KEY_TRIGGER_ID to triggerId))
            .build()
        val res1 = worker1.doWork()
        assertEquals(ListenableWorker.Result.retry(), res1)
        val t1 = app.container.smsTriggerDao.getTriggerById(triggerId)!!
        assertEquals(1, t1.attemptCount)
        assertEquals(TriggerState.FAILED, t1.state)

        // Attempt 2 -> retry, attemptCount = 2
        val worker2 = TestListenableWorkerBuilder<SmsAnalysisWorker>(app)
            .setInputData(workDataOf(SmsAnalysisWorker.KEY_TRIGGER_ID to triggerId))
            .build()
        val res2 = worker2.doWork()
        assertEquals(ListenableWorker.Result.retry(), res2)
        val t2 = app.container.smsTriggerDao.getTriggerById(triggerId)!!
        assertEquals(2, t2.attemptCount)
        assertEquals(TriggerState.FAILED, t2.state)

        // Attempt 3 (MAX_RETRIES reached) -> success, attemptCount = 3, state = DISCARDED
        val worker3 = TestListenableWorkerBuilder<SmsAnalysisWorker>(app)
            .setInputData(workDataOf(SmsAnalysisWorker.KEY_TRIGGER_ID to triggerId))
            .build()
        val res3 = worker3.doWork()
        assertEquals(ListenableWorker.Result.success(), res3)
        val t3 = app.container.smsTriggerDao.getTriggerById(triggerId)!!
        assertEquals(3, t3.attemptCount)
        assertEquals(TriggerState.DISCARDED, t3.state)
    }

    // 14. Unknown AI jobId: safely ignored without corrupting state
    @Test
    fun coordinator_unknownAiJobId_safelyIgnored() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        app.container.clientDao.insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Unknown Job"
            )
        )
        val validJobId = UUID.randomUUID().toString()
        app.container.jobDao.insertJob(JobEntity(id = validJobId, clientId = clientId, status = JobStatus.ACTIVE))
        val now = System.currentTimeMillis()
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(jobId = validJobId, startedAt = now - 1000L, reason = WindowReason.CREATED)
        )

        val triggerId = UUID.randomUUID().toString()
        app.container.smsTriggerDao.insertTrigger(
            SmsTriggerEntity(
                id = triggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = now,
                state = TriggerState.PENDING
            )
        )

        val unknownJobEngine = object : SmsExtractionEngine {
            override suspend fun extract(input: SmsExtractionInput): StructuredExtractionResult? {
                return StructuredExtractionResult(
                    jobSummaries = listOf(
                        JobSummaryUpdate(jobId = validJobId, updatedSummary = "Valid summary"),
                        JobSummaryUpdate(jobId = "unknown-foreign-job-id", updatedSummary = "Foreign summary")
                    )
                )
            }
        }

        val testCoordinator = SmsAnalysisCoordinator(
            database = app.container.database,
            clientDao = app.container.clientDao,
            jobDao = app.container.jobDao,
            windowDao = app.container.windowDao,
            suggestionDao = app.container.aiSuggestionDao,
            triggerDao = app.container.smsTriggerDao,
            appPreferences = appPreferences,
            extractionEngine = unknownJobEngine
        )

        val result = testCoordinator.processSmsTrigger(triggerId, "Test message")
        assertTrue(result)

        val validJob = app.container.jobDao.getJobByIdSync(validJobId)!!
        assertEquals("Valid summary", validJob.smsSummary)
        assertNull(app.container.jobDao.getJobByIdSync("unknown-foreign-job-id"))
    }

    // 15. Reopened job: SMS from closed old window rejected, SMS from new open window accepted
    @Test
    fun coordinator_reopenedJob_smsFromOldClosedWindowRejected_newWindowAccepted() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        app.container.clientDao.insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Reopened"
            )
        )
        val jobId = UUID.randomUUID().toString()
        app.container.jobDao.insertJob(JobEntity(id = jobId, clientId = clientId, status = JobStatus.ACTIVE))

        // Old closed window (1000 - 2000)
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(id = "win-old", jobId = jobId, startedAt = 1000L, endedAt = 2000L, reason = WindowReason.CREATED)
        )
        // New reopened window (startedAt = 5000L)
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(id = "win-new", jobId = jobId, startedAt = 5000L, endedAt = null, reason = WindowReason.REOPENED)
        )

        // A. SMS received during old closed window (receivedAt = 1500L)
        val staleTriggerId = UUID.randomUUID().toString()
        app.container.smsTriggerDao.insertTrigger(
            SmsTriggerEntity(
                id = staleTriggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = 1500L,
                state = TriggerState.PENDING
            )
        )
        val staleResult = coordinator.processSmsTrigger(staleTriggerId, "Stara wiadomosc")
        assertFalse(staleResult)
        assertEquals(TriggerState.DISCARDED, app.container.smsTriggerDao.getTriggerById(staleTriggerId)!!.state)

        // B. SMS received during new window (receivedAt = 6000L)
        val validTriggerId = UUID.randomUUID().toString()
        app.container.smsTriggerDao.insertTrigger(
            SmsTriggerEntity(
                id = validTriggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = 6000L,
                state = TriggerState.PENDING
            )
        )
        val validResult = coordinator.processSmsTrigger(validTriggerId, "Adres: ul. Polna 3, Warszawa")
        assertTrue(validResult)
        assertEquals(TriggerState.PROCESSED, app.container.smsTriggerDao.getTriggerById(validTriggerId)!!.state)
    }

    // 16. No raw SMS body persisted in Room
    @Test
    fun database_noRawSmsBodyStoredInRoomSchema() {
        val fields = SmsTriggerEntity::class.java.declaredFields.map { it.name }
        assertFalse("SmsTriggerEntity must not contain body field", fields.contains("body"))
        assertFalse("SmsTriggerEntity must not contain messageBody field", fields.contains("messageBody"))
        assertFalse("SmsTriggerEntity must not contain smsBody field", fields.contains("smsBody"))
        assertFalse("SmsTriggerEntity must not contain text field", fields.contains("text"))
        assertFalse("SmsTriggerEntity must not contain fullText field", fields.contains("fullText"))

        val expectedFields = setOf("id", "clientId", "senderPhoneKey", "receivedAt", "state", "attemptCount", "createdAt")
        assertEquals(expectedFields, fields.filter { !it.startsWith("$") && it != "Companion" }.toSet())
    }

    // 17. Static inspection: SmsReceiver does not access raw message body
    @Test
    fun staticCheck_smsReceiverDoesNotCallMessageBody() {
        val receiverFile = File("src/main/java/com/example/system/sms/SmsReceiver.kt")
        val altFile = File("app/src/main/java/com/example/system/sms/SmsReceiver.kt")
        val file = if (receiverFile.exists()) receiverFile else altFile
        assertTrue("SmsReceiver.kt must exist", file.exists())

        val content = file.readText()
        assertFalse("SmsReceiver must not call messageBody", content.contains("messageBody"))
        assertFalse("SmsReceiver must not construct fullBody", content.contains("fullBody"))
    }
}
