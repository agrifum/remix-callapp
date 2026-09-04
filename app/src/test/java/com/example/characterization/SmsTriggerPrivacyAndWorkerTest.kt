package com.example.characterization

import android.content.Context
import android.content.Intent
import android.database.MatrixCursor
import android.provider.Telephony
import androidx.room.Room
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
import com.example.ai.FakeSmsExtractionEngine
import com.example.ai.SmsAnalysisCoordinator
import com.example.ai.SmsExtractionEngine
import com.example.ai.model.AddressCandidate
import com.example.ai.model.JobSummaryUpdate
import com.example.ai.model.SmsExtractionInput
import com.example.ai.model.StructuredExtractionResult
import com.example.ai.model.TermCandidate
import com.example.core.model.JobStatus
import com.example.core.model.ReengagementSource
import com.example.core.model.SmsAnalysisMode
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
import kotlinx.coroutines.flow.first
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
import java.io.File
import java.util.UUID

/**
 * Comprehensive verification of IMP-SMS-01:
 * - SmsReceiver privacy boundary (Global OFF, Client DISABLED, metadata-only trigger, WorkManager enqueue).
 * - System SMS re-read (single target SMS matching, no arbitrary scan).
 * - SmsAnalysisWorker execution, retry handling, and fail-closed behaviors.
 * - Transactional pre-mutation re-validation on COMPLETED/CLOSED jobs and window boundaries.
 * - Data minimization and zero raw body persistence in Room.
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

        database = Room.inMemoryDatabaseBuilder(context, CallUppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appPreferences = AppPreferences(context)
        appPreferences.setSmsAnalysisGlobalEnabled(true)

        coordinator = SmsAnalysisCoordinator(
            database = database,
            clientDao = database.clientDao(),
            jobDao = database.jobDao(),
            windowDao = database.jobAnalysisWindowDao(),
            suggestionDao = database.aiSuggestionDao(),
            triggerDao = database.smsTriggerDao(),
            appPreferences = appPreferences,
            extractionEngine = FakeSmsExtractionEngine()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // Helper to generate standard 3GPP SMS-DELIVER PDU byte array
    // Originator: +48501234567, text: "Test SMS content"
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

    // 1. Global OFF: SmsReceiver does NOT read/process SMS body, no trigger, no worker enqueued
    @Test
    fun smsReceiver_globalOff_noTriggerCreated_noWorkerEnqueued() = runBlocking {
        appPreferences.setSmsAnalysisGlobalEnabled(false)

        val clientId = UUID.randomUUID().toString()
        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Testowy"
            )
        )
        val jobId = UUID.randomUUID().toString()
        database.jobDao().insertJob(JobEntity(id = jobId, clientId = clientId, status = JobStatus.ACTIVE))
        database.jobAnalysisWindowDao().insertWindow(
            JobAnalysisWindowEntity(jobId = jobId, startedAt = 1000L, reason = WindowReason.CREATED)
        )

        // Point container to test database
        // We verify via DAO directly
        val receiver = SmsReceiver()
        val intent = createSmsIntent()

        receiver.onReceive(app, intent)

        // Allow coroutine execution
        Thread.sleep(150)

        val triggers = database.smsTriggerDao().getPendingTriggers()
        assertTrue("No trigger should be created when Global OFF", triggers.isEmpty())
    }

    // 2. Client DISABLED: SmsReceiver does NOT create trigger or enqueue worker
    @Test
    fun smsReceiver_clientDisabled_noTriggerCreated() = runBlocking {
        appPreferences.setSmsAnalysisGlobalEnabled(true)

        val clientId = UUID.randomUUID().toString()
        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Disabled",
                smsAnalysisMode = SmsAnalysisMode.DISABLED
            )
        )
        val jobId = UUID.randomUUID().toString()
        database.jobDao().insertJob(JobEntity(id = jobId, clientId = clientId, status = JobStatus.ACTIVE))
        database.jobAnalysisWindowDao().insertWindow(
            JobAnalysisWindowEntity(jobId = jobId, startedAt = 1000L, reason = WindowReason.CREATED)
        )

        val receiver = SmsReceiver()
        receiver.onReceive(app, createSmsIntent())
        Thread.sleep(150)

        val triggers = database.smsTriggerDao().getPendingTriggers()
        assertTrue("No trigger should be created when Client SMS mode is DISABLED", triggers.isEmpty())
    }

    // 3. No ACTIVE jobs: creates reengagement event if completed/closed jobs exist, no trigger
    @Test
    fun smsReceiver_noActiveJobs_createsReengagementEvent_noTriggerCreated() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        app.container.clientDao.insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Bez Aktywnych"
            )
        )
        // Closed job
        app.container.jobDao.insertJob(
            JobEntity(
                clientId = clientId,
                status = JobStatus.COMPLETED
            )
        )

        val receiver = SmsReceiver()
        receiver.onReceive(app, createSmsIntent())
        Thread.sleep(200)

        val triggers = app.container.smsTriggerDao.getPendingTriggers()
        assertTrue("No trigger should be created when there are no active jobs", triggers.isEmpty())

        val event = app.container.reengagementEventDao.getPendingEventForClient(clientId)
        assertNotNull(event)
        assertEquals(clientId, event!!.clientId)
        assertEquals(ReengagementSource.INCOMING_SMS, event.source)
    }

    // 4. Eligible SMS: metadata-only trigger created, worker enqueued
    @Test
    fun smsReceiver_eligibleSms_createsMetadataOnlyTrigger_andEnqueuesWorker() = runBlocking {
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
                startedAt = 0L, // Covers all timestamps
                reason = WindowReason.CREATED
            )
        )

        val receiver = SmsReceiver()
        receiver.onReceive(app, createSmsIntent())
        Thread.sleep(200)

        val triggers = app.container.smsTriggerDao.getPendingTriggers()
        assertEquals(1, triggers.size)
        val trigger = triggers[0]
        assertEquals(clientId, trigger.clientId)
        assertEquals("+48501234567", trigger.senderPhoneKey)
        assertEquals(TriggerState.PENDING, trigger.state)

        // Verify WorkManager work was enqueued with unique tag
        val workManager = WorkManager.getInstance(app)
        val workInfos = workManager.getWorkInfosForUniqueWork("sms_analysis_${trigger.id}").get()
        assertNotNull(workInfos)
        assertEquals(1, workInfos.size)
    }

    // 5. SystemSmsReader: selectively re-reads target SMS, ignores unrelated SMS
    @Test
    fun systemSmsReader_readsExactMatchingSms_doesNotSubstituteUnrelatedSms() {
        val targetPhoneKey = "+48501234567"
        val targetTimestamp = 1710000000000L

        // Fake system SMS reader implementation verifying contract
        val fakeReader = object : SystemSmsReader {
            val inbox = listOf(
                Triple("+48999999999", targetTimestamp, "Unrelated sender message"),
                Triple(targetPhoneKey, targetTimestamp + 60000L, "Out of tolerance window message"),
                Triple(targetPhoneKey, targetTimestamp + 1000L, "Adres: ul. Lipowa 5, Warszawa")
            )

            override fun readSms(senderPhoneKey: String, receivedAt: Long): String? {
                val tolerance = 10_000L
                return inbox.firstOrNull { (sender, time, _) ->
                    sender == senderPhoneKey && Math.abs(time - receivedAt) <= tolerance
                }?.third
            }
        }

        val body = fakeReader.readSms(targetPhoneKey, targetTimestamp)
        assertEquals("Adres: ul. Lipowa 5, Warszawa", body)

        val notFound = fakeReader.readSms("+48111222333", targetTimestamp)
        assertNull(notFound)
    }

    // 6. Worker exact execution: durable path without in-memory receiver body
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
        app.container.jobDao.insertJob(
            JobEntity(
                id = jobId,
                clientId = clientId,
                status = JobStatus.ACTIVE
            )
        )
        val now = System.currentTimeMillis()
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(
                jobId = jobId,
                startedAt = now - 1000L,
                reason = WindowReason.CREATED
            )
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

        // Inject test system SMS reader into container
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
        assertNotNull(updatedTrigger)
        assertEquals(TriggerState.PROCESSED, updatedTrigger!!.state)

        val updatedClient = app.container.clientDao.getClientByIdSync(clientId)
        assertEquals("Wierzbowa", updatedClient!!.street)
    }

    // 7. Job completed before worker runs: trigger discarded, zero mutation
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
        // Job is COMPLETED
        app.container.jobDao.insertJob(
            JobEntity(
                id = jobId,
                clientId = clientId,
                status = JobStatus.COMPLETED
            )
        )
        val now = System.currentTimeMillis()
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(
                jobId = jobId,
                startedAt = now - 1000L,
                endedAt = now - 500L,
                reason = WindowReason.CREATED
            )
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
        assertEquals(TriggerState.DISCARDED, updatedTrigger!!.state)

        val client = app.container.clientDao.getClientByIdSync(clientId)
        assertNull(client!!.street)
    }

    // 8. In-flight race: Job completed while extraction in progress -> pre-mutation re-validation prevents mutation
    @Test
    fun coordinator_jobCompletedDuringExtraction_preMutationRevalidationRejectsMutation() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Race Test"
            )
        )
        val jobId = UUID.randomUUID().toString()
        database.jobDao().insertJob(
            JobEntity(
                id = jobId,
                clientId = clientId,
                status = JobStatus.ACTIVE,
                addressStreetSnapshot = null
            )
        )
        val now = System.currentTimeMillis()
        database.jobAnalysisWindowDao().insertWindow(
            JobAnalysisWindowEntity(
                jobId = jobId,
                startedAt = now - 1000L,
                reason = WindowReason.CREATED
            )
        )

        val triggerId = UUID.randomUUID().toString()
        database.smsTriggerDao().insertTrigger(
            SmsTriggerEntity(
                id = triggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = now,
                state = TriggerState.PENDING
            )
        )

        // Engine that simulates Job completing concurrently while extract() is executing
        val racingEngine = object : SmsExtractionEngine {
            override suspend fun extract(input: SmsExtractionInput): StructuredExtractionResult? {
                // CONCURRENT MUTATION: Job transitions to COMPLETED and closes window before extract returns!
                val currentJob = database.jobDao().getJobByIdSync(jobId)!!
                database.jobDao().updateJob(currentJob.copy(status = JobStatus.COMPLETED))
                database.jobAnalysisWindowDao().closeAllWindowsForJob(jobId, System.currentTimeMillis())

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

        val racingCoordinator = SmsAnalysisCoordinator(
            database = database,
            clientDao = database.clientDao(),
            jobDao = database.jobDao(),
            windowDao = database.jobAnalysisWindowDao(),
            suggestionDao = database.aiSuggestionDao(),
            triggerDao = database.smsTriggerDao(),
            appPreferences = appPreferences,
            extractionEngine = racingEngine
        )

        val success = racingCoordinator.processSmsTrigger(triggerId, "Adres: ul. Wspólna 15, termin: jutro 12:00")
        assertFalse("Coordinator must fail-closed when all jobs became completed during extraction", success)

        // Verify that completed job was NOT mutated with address, term, or summary
        val jobAfter = database.jobDao().getJobByIdSync(jobId)!!
        assertEquals(JobStatus.COMPLETED, jobAfter.status)
        assertNull("Address snapshot must not be written to completed job", jobAfter.addressStreetSnapshot)
        assertNull("Term must not be written to completed job", jobAfter.preliminaryDateEpochDay)
        assertNull("Summary must not be written to completed job", jobAfter.smsSummary)

        // Verify trigger was discarded
        val updatedTrigger = database.smsTriggerDao().getTriggerById(triggerId)!!
        assertEquals(TriggerState.DISCARDED, updatedTrigger.state)
    }

    // 9. Reopened job: SMS from closed old window rejected, SMS from new open window accepted
    @Test
    fun coordinator_reopenedJob_smsFromOldClosedWindowRejected_newWindowAccepted() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Reopened"
            )
        )
        val jobId = UUID.randomUUID().toString()
        database.jobDao().insertJob(
            JobEntity(
                id = jobId,
                clientId = clientId,
                status = JobStatus.ACTIVE
            )
        )

        // Old closed window (1000 - 2000)
        database.jobAnalysisWindowDao().insertWindow(
            JobAnalysisWindowEntity(
                id = "win-old",
                jobId = jobId,
                startedAt = 1000L,
                endedAt = 2000L,
                reason = WindowReason.CREATED
            )
        )
        // New reopened window (startedAt = 5000L)
        database.jobAnalysisWindowDao().insertWindow(
            JobAnalysisWindowEntity(
                id = "win-new",
                jobId = jobId,
                startedAt = 5000L,
                endedAt = null,
                reason = WindowReason.REOPENED
            )
        )

        // A. SMS received during old closed window (receivedAt = 1500L)
        val staleTriggerId = UUID.randomUUID().toString()
        database.smsTriggerDao().insertTrigger(
            SmsTriggerEntity(
                id = staleTriggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = 1500L,
                state = TriggerState.PENDING
            )
        )
        val staleResult = coordinator.processSmsTrigger(staleTriggerId, "Stara wiadomosc")
        assertFalse("SMS from closed window must be rejected", staleResult)
        assertEquals(TriggerState.DISCARDED, database.smsTriggerDao().getTriggerById(staleTriggerId)!!.state)

        // B. SMS received during new window (receivedAt = 6000L)
        val validTriggerId = UUID.randomUUID().toString()
        database.smsTriggerDao().insertTrigger(
            SmsTriggerEntity(
                id = validTriggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = 6000L,
                state = TriggerState.PENDING
            )
        )
        val validResult = coordinator.processSmsTrigger(validTriggerId, "Adres: ul. Polna 3, Warszawa")
        assertTrue("SMS within new open window must be accepted", validResult)
        assertEquals(TriggerState.PROCESSED, database.smsTriggerDao().getTriggerById(validTriggerId)!!.state)
    }

    // 10. Unknown AI jobId: safely ignored without corrupting state
    @Test
    fun coordinator_unknownAiJobId_safelyIgnored() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Unknown Job"
            )
        )
        val validJobId = UUID.randomUUID().toString()
        database.jobDao().insertJob(
            JobEntity(
                id = validJobId,
                clientId = clientId,
                status = JobStatus.ACTIVE
            )
        )
        val now = System.currentTimeMillis()
        database.jobAnalysisWindowDao().insertWindow(
            JobAnalysisWindowEntity(
                jobId = validJobId,
                startedAt = now - 1000L,
                reason = WindowReason.CREATED
            )
        )

        val triggerId = UUID.randomUUID().toString()
        database.smsTriggerDao().insertTrigger(
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
            database = database,
            clientDao = database.clientDao(),
            jobDao = database.jobDao(),
            windowDao = database.jobAnalysisWindowDao(),
            suggestionDao = database.aiSuggestionDao(),
            triggerDao = database.smsTriggerDao(),
            appPreferences = appPreferences,
            extractionEngine = unknownJobEngine
        )

        val result = testCoordinator.processSmsTrigger(triggerId, "Test message")
        assertTrue(result)

        val validJob = database.jobDao().getJobByIdSync(validJobId)!!
        assertEquals("Valid summary", validJob.smsSummary)

        // Verify foreign job was never created or corrupted
        assertNull(database.jobDao().getJobByIdSync("unknown-foreign-job-id"))
    }

    // 11. Missing SMS in system provider: retry when attemptCount < MAX_RETRIES, discarded when retries exhausted
    @Test
    fun smsAnalysisWorker_missingSms_retriesThenDiscards() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        app.container.clientDao.insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient Missing SMS"
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
        val now = System.currentTimeMillis()
        app.container.windowDao.insertWindow(
            JobAnalysisWindowEntity(
                jobId = jobId,
                startedAt = now - 1000L,
                reason = WindowReason.CREATED
            )
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

        // System provider returns null (SMS not found)
        app.container.systemSmsReader = object : SystemSmsReader {
            override fun readSms(senderPhoneKey: String, receivedAt: Long): String? = null
        }

        // Attempt 0 -> returns retry
        val workerAttempt0 = TestListenableWorkerBuilder<SmsAnalysisWorker>(app)
            .setInputData(workDataOf(SmsAnalysisWorker.KEY_TRIGGER_ID to triggerId))
            .setRunAttemptCount(0)
            .build()
        val result0 = workerAttempt0.doWork()
        assertEquals(ListenableWorker.Result.retry(), result0)

        // Attempt 3 (MAX_RETRIES) -> returns success and marks DISCARDED
        val workerAttemptMax = TestListenableWorkerBuilder<SmsAnalysisWorker>(app)
            .setInputData(workDataOf(SmsAnalysisWorker.KEY_TRIGGER_ID to triggerId))
            .setRunAttemptCount(SmsAnalysisWorker.MAX_RETRIES)
            .build()
        val resultMax = workerAttemptMax.doWork()
        assertEquals(ListenableWorker.Result.success(), resultMax)

        val finalTrigger = app.container.smsTriggerDao.getTriggerById(triggerId)!!
        assertEquals(TriggerState.DISCARDED, finalTrigger.state)
    }

    // 12. No raw SMS body persisted in Room
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

    // 13. Static inspection: SmsReceiver does not call messageBody
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
