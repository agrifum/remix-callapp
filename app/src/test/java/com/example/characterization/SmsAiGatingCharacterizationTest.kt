package com.example.characterization

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.FakeSmsExtractionEngine
import com.example.ai.SmsAnalysisCoordinator
import com.example.ai.SmsExtractionEngine
import com.example.ai.model.SmsExtractionInput
import com.example.ai.model.StructuredExtractionResult
import com.example.core.model.JobStatus
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Characterization tests for [SmsAnalysisCoordinator] gating, eligibility rules,
 * fail-closed behaviors, and automatic update vs suggestion protection semantics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SmsAiGatingCharacterizationTest {

    private lateinit var database: CallUppDatabase
    private lateinit var appPreferences: AppPreferences
    private lateinit var coordinator: SmsAnalysisCoordinator

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
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

    @Test
    fun processSmsTrigger_globalAnalysisDisabled_discardsTriggerAndReturnsFalse() = runBlocking {
        appPreferences.setSmsAnalysisGlobalEnabled(false)

        val triggerId = UUID.randomUUID().toString()
        val trigger = SmsTriggerEntity(
            id = triggerId,
            clientId = "any-client",
            senderPhoneKey = "+48501234567",
            receivedAt = System.currentTimeMillis(),
            state = TriggerState.PENDING
        )
        database.smsTriggerDao().insertTrigger(trigger)

        val result = coordinator.processSmsTrigger(triggerId, "Adres: ul. Lipowa 5, Warszawa")
        assertFalse(result)

        val updatedTrigger = database.smsTriggerDao().getTriggerById(triggerId)
        assertNotNull(updatedTrigger)
        assertEquals(TriggerState.DISCARDED, updatedTrigger!!.state)
    }

    @Test
    fun processSmsTrigger_clientAnalysisDisabled_discardsTriggerAndReturnsFalse() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient zablokowany SMS",
                smsAnalysisMode = SmsAnalysisMode.DISABLED
            )
        )

        val triggerId = UUID.randomUUID().toString()
        database.smsTriggerDao().insertTrigger(
            SmsTriggerEntity(
                id = triggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = System.currentTimeMillis()
            )
        )

        val result = coordinator.processSmsTrigger(triggerId, "ul. Lipowa 5, Warszawa")
        assertFalse(result)
        assertEquals(TriggerState.DISCARDED, database.smsTriggerDao().getTriggerById(triggerId)!!.state)
    }

    @Test
    fun processSmsTrigger_clientNotFound_discardsTriggerAndReturnsFalse() = runBlocking {
        val triggerId = UUID.randomUUID().toString()
        database.smsTriggerDao().insertTrigger(
            SmsTriggerEntity(
                id = triggerId,
                clientId = "non-existent-client",
                senderPhoneKey = "+48501234567",
                receivedAt = System.currentTimeMillis()
            )
        )

        val result = coordinator.processSmsTrigger(triggerId, "ul. Lipowa 5")
        assertFalse(result)
        assertEquals(TriggerState.DISCARDED, database.smsTriggerDao().getTriggerById(triggerId)!!.state)
    }

    @Test
    fun processSmsTrigger_clientHasNoActiveJobs_discardsTriggerAndReturnsFalse() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient bez aktywnych zleceń"
            )
        )
        database.jobDao().insertJob(
            JobEntity(
                clientId = clientId,
                status = JobStatus.COMPLETED
            )
        )

        val triggerId = UUID.randomUUID().toString()
        database.smsTriggerDao().insertTrigger(
            SmsTriggerEntity(
                id = triggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = System.currentTimeMillis()
            )
        )

        val result = coordinator.processSmsTrigger(triggerId, "ul. Lipowa 5, Warszawa")
        assertFalse(result)
        assertEquals(TriggerState.DISCARDED, database.smsTriggerDao().getTriggerById(triggerId)!!.state)
    }

    @Test
    fun processSmsTrigger_smsReceivedBeforeOpenWindowStarted_discardsTrigger() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient ze starym SMS"
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

        val windowStartTime = 1710000000000L
        database.jobAnalysisWindowDao().insertWindow(
            JobAnalysisWindowEntity(
                jobId = jobId,
                startedAt = windowStartTime,
                reason = WindowReason.CREATED
            )
        )

        val triggerId = UUID.randomUUID().toString()
        database.smsTriggerDao().insertTrigger(
            SmsTriggerEntity(
                id = triggerId,
                clientId = clientId,
                senderPhoneKey = "+48501234567",
                receivedAt = windowStartTime - 10000L
            )
        )

        val result = coordinator.processSmsTrigger(triggerId, "ul. Lipowa 5, Warszawa")
        assertFalse(result)
        assertEquals(TriggerState.DISCARDED, database.smsTriggerDao().getTriggerById(triggerId)!!.state)
    }

    @Test
    fun processSmsTrigger_emptyClientAddress_safelyFilledAndPropagatedToEmptyJob() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient bez adresu"
            )
        )
        val jobId = UUID.randomUUID().toString()
        database.jobDao().insertJob(
            JobEntity(
                id = jobId,
                clientId = clientId,
                status = JobStatus.ACTIVE,
                addressCitySnapshot = null,
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
                receivedAt = now
            )
        )

        val result = coordinator.processSmsTrigger(triggerId, "Adres: ul. Kwiatowa 12, Warszawa")
        assertTrue(result)

        val updatedClient = database.clientDao().getClientByIdSync(clientId)
        assertNotNull(updatedClient)
        assertEquals("Kwiatowa", updatedClient!!.street)
        assertEquals("12", updatedClient.buildingNumber)
        assertEquals("Warszawa", updatedClient.city)

        val updatedJob = database.jobDao().getJobByIdSync(jobId)
        assertNotNull(updatedJob)
        assertEquals("Kwiatowa", updatedJob!!.addressStreetSnapshot)
        assertEquals("12", updatedJob.addressBuildingSnapshot)
        assertEquals("Warszawa", updatedJob.addressCitySnapshot)

        assertEquals(TriggerState.PROCESSED, database.smsTriggerDao().getTriggerById(triggerId)!!.state)
    }

    @Test
    fun processSmsTrigger_existingClientAddress_neverOverwritesAutomatically_createsSuggestion() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient ze starym adresem",
                street = "Złota",
                buildingNumber = "5",
                city = "Warszawa"
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
                receivedAt = now
            )
        )

        val result = coordinator.processSmsTrigger(triggerId, "Nowy adres: ul. Kwiatowa 12, Warszawa")
        assertTrue(result)

        val client = database.clientDao().getClientByIdSync(clientId)
        assertEquals("Złota", client!!.street)
        assertEquals("5", client.buildingNumber)

        val suggestions = database.aiSuggestionDao().getPendingSuggestionsForClient(clientId).first()
        assertEquals(1, suggestions.size)
        val suggestion = suggestions.first()
        assertEquals(SuggestionType.ADDRESS_CHANGE, suggestion.type)
        assertEquals(SuggestionStatus.PENDING, suggestion.status)
        assertTrue(suggestion.proposedValueJson.contains("Kwiatowa"))
    }

    @Test
    fun processSmsTrigger_existingJobTerm_neverOverwritesAutomatically_createsSuggestion() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient z ustalonym terminem"
            )
        )
        val existingDate = 20000L
        val jobId = UUID.randomUUID().toString()
        database.jobDao().insertJob(
            JobEntity(
                id = jobId,
                clientId = clientId,
                status = JobStatus.ACTIVE,
                preliminaryDateEpochDay = existingDate,
                preliminaryTimeMinute = 600,
                preliminaryTimeQualifier = TimeQualifier.EXACT
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
                receivedAt = now
            )
        )

        val result = coordinator.processSmsTrigger(triggerId, "Możemy przełożyć na jutro o 14:00?")
        assertTrue(result)

        val job = database.jobDao().getJobByIdSync(jobId)
        assertEquals(existingDate, job!!.preliminaryDateEpochDay)
        assertEquals(600, job.preliminaryTimeMinute)

        val suggestions = database.aiSuggestionDao().getPendingSuggestionsForJobSync(jobId)
        assertEquals(1, suggestions.size)
        val suggestion = suggestions.first()
        assertEquals(SuggestionType.TERM_CHANGE, suggestion.type)
        assertEquals(SuggestionStatus.PENDING, suggestion.status)
        assertEquals(jobId, suggestion.targetJobId)
    }

    @Test
    fun processSmsTrigger_emptyJobTerm_safelyPopulatedWithCandidate() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient z pustym terminem"
            )
        )
        val jobId = UUID.randomUUID().toString()
        database.jobDao().insertJob(
            JobEntity(
                id = jobId,
                clientId = clientId,
                status = JobStatus.ACTIVE,
                preliminaryDateEpochDay = null,
                preliminaryTimeMinute = null,
                confirmedStartAt = null
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
                receivedAt = now
            )
        )

        val result = coordinator.processSmsTrigger(triggerId, "Będę czekać jutro około 14:00")
        assertTrue(result)

        val job = database.jobDao().getJobByIdSync(jobId)
        assertNotNull(job!!.preliminaryDateEpochDay)
        assertEquals(14 * 60, job.preliminaryTimeMinute)
        assertEquals(TimeQualifier.AROUND, job.preliminaryTimeQualifier)
    }

    @Test
    fun processSmsTrigger_extractionEngineThrowsException_marksTriggerFailedAndReturnsFalse() = runBlocking {
        val clientId = UUID.randomUUID().toString()
        database.clientDao().insertClient(
            ClientEntity(
                id = clientId,
                phoneKey = "+48501234567",
                phoneDisplay = "+48 501 234 567",
                displayName = "Klient test błędu"
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
                receivedAt = now
            )
        )

        val failingCoordinator = SmsAnalysisCoordinator(
            database = database,
            clientDao = database.clientDao(),
            jobDao = database.jobDao(),
            windowDao = database.jobAnalysisWindowDao(),
            suggestionDao = database.aiSuggestionDao(),
            triggerDao = database.smsTriggerDao(),
            appPreferences = appPreferences,
            extractionEngine = object : SmsExtractionEngine {
                override suspend fun extract(input: SmsExtractionInput): StructuredExtractionResult? {
                    throw IllegalStateException("Simulated extraction error")
                }
            }
        )

        val result = failingCoordinator.processSmsTrigger(triggerId, "Test sms")
        assertFalse(result)
        assertEquals(TriggerState.FAILED, database.smsTriggerDao().getTriggerById(triggerId)!!.state)
    }
}