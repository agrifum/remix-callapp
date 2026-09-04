package com.example.ai

import androidx.room.withTransaction
import com.example.ai.model.AddressCandidate
import com.example.ai.model.ClientAddressInput
import com.example.ai.model.SmsExtractionInput
import com.example.ai.model.TermCandidate
import com.example.core.model.JobStatus
import com.example.core.model.SmsAnalysisMode
import com.example.core.model.SuggestionStatus
import com.example.core.model.SuggestionType
import com.example.core.model.TriggerState
import com.example.core.time.DateTimeFormatters
import com.example.data.database.CallUppDatabase
import com.example.data.dao.AiSuggestionDao
import com.example.data.dao.ClientDao
import com.example.data.dao.JobAnalysisWindowDao
import com.example.data.dao.JobDao
import com.example.data.dao.SmsTriggerDao
import com.example.data.entity.AiSuggestionEntity
import com.example.data.entity.JobEntity
import com.example.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class SmsAnalysisCoordinator(
    private val database: CallUppDatabase,
    private val clientDao: ClientDao,
    private val jobDao: JobDao,
    private val windowDao: JobAnalysisWindowDao,
    private val suggestionDao: AiSuggestionDao,
    private val triggerDao: SmsTriggerDao,
    private val appPreferences: AppPreferences,
    private val extractionEngine: SmsExtractionEngine
) {

    /**
     * Process an incoming SMS trigger through the extraction pipeline with strict pre-write
     * revalidation of global settings, client mode, and Job ACTIVE / window status.
     */
    suspend fun processSmsTrigger(triggerId: String, smsBody: String): Boolean {
        val trigger = triggerDao.getTriggerById(triggerId) ?: return false

        // 1. Check global and client eligibility before running extraction
        val globalEnabled = appPreferences.smsAnalysisGlobalEnabled.first()
        if (!globalEnabled) {
            triggerDao.updateState(triggerId, TriggerState.DISCARDED)
            return false
        }

        val client = clientDao.getClientByIdSync(trigger.clientId)
        if (client == null) {
            triggerDao.updateState(triggerId, TriggerState.DISCARDED)
            return false
        }

        if (client.smsAnalysisMode == SmsAnalysisMode.DISABLED) {
            triggerDao.updateState(triggerId, TriggerState.DISCARDED)
            return false
        }

        // 2. Client must have at least one ACTIVE Job
        val activeJobs = jobDao.getActiveJobsForClientSync(client.id)
        if (activeJobs.isEmpty()) {
            triggerDao.updateState(triggerId, TriggerState.DISCARDED)
            return false
        }

        // 3. Filter jobs with valid open analysis windows covering the SMS arrival timestamp
        val eligibleJobs = mutableListOf<JobEntity>()
        for (job in activeJobs) {
            val window = windowDao.getOpenWindowForJob(job.id)
            if (window != null && window.endedAt == null && trigger.receivedAt >= window.startedAt) {
                eligibleJobs.add(job)
            }
        }

        if (eligibleJobs.isEmpty()) {
            triggerDao.updateState(triggerId, TriggerState.DISCARDED)
            return false
        }

        // 4. Prepare sanitized input for extraction engine (input data minimization)
        val clientAddress = ClientAddressInput(
            city = client.city,
            district = client.district,
            street = client.street,
            buildingNumber = client.buildingNumber,
            unitNumber = client.unitNumber,
            postalCode = client.postalCode
        )

        val termsMap = eligibleJobs.associate { job ->
            job.id to DateTimeFormatters.formatJobTerm(
                job.preliminaryDateEpochDay,
                job.preliminaryTimeMinute,
                job.preliminaryTimeQualifier,
                job.confirmedStartAt
            )
        }

        val summariesMap = eligibleJobs.associate { job ->
            job.id to (job.smsSummary ?: "")
        }

        val now = LocalDateTime.now()
        val input = SmsExtractionInput(
            smsBody = smsBody,
            receivedTimestamp = trigger.receivedAt,
            localDateTime = now.toString(),
            timezone = ZoneId.systemDefault().id,
            clientAddress = clientAddress,
            activeJobIds = eligibleJobs.map { it.id },
            activeJobTerms = termsMap,
            activeJobSummaries = summariesMap
        )

        // 5. Run extraction engine
        val extractionResult = try {
            extractionEngine.extract(input)
        } catch (e: Exception) {
            triggerDao.updateState(triggerId, TriggerState.FAILED)
            return false
        }

        if (extractionResult == null) {
            triggerDao.updateState(triggerId, TriggerState.DISCARDED)
            return false
        }

        // 6. Apply field protection rules inside transaction with full re-validation
        return database.withTransaction {
            // Re-verify global setting
            val currentGlobalEnabled = appPreferences.smsAnalysisGlobalEnabled.first()
            if (!currentGlobalEnabled) {
                triggerDao.updateState(triggerId, TriggerState.DISCARDED)
                return@withTransaction false
            }

            // Re-verify client and analysis mode
            val currentClient = clientDao.getClientByIdSync(trigger.clientId)
            if (currentClient == null || currentClient.smsAnalysisMode == SmsAnalysisMode.DISABLED) {
                triggerDao.updateState(triggerId, TriggerState.DISCARDED)
                return@withTransaction false
            }

            // Helper to re-verify that a Job is still ACTIVE, not deleted, and has an open window covering receivedAt
            suspend fun getStillValidJob(jobId: String): JobEntity? {
                val job = jobDao.getJobByIdSync(jobId) ?: return null
                if (job.status != JobStatus.ACTIVE || job.isArchived || job.deletedAt != null) return null
                val window = windowDao.getOpenWindowForJob(job.id) ?: return null
                if (window.endedAt != null || trigger.receivedAt < window.startedAt) return null
                return job
            }

            // Re-fetch still-valid jobs at mutation time
            val currentEligibleJobs = mutableListOf<JobEntity>()
            for (job in eligibleJobs) {
                val valid = getStillValidJob(job.id)
                if (valid != null) {
                    currentEligibleJobs.add(valid)
                }
            }
            if (currentEligibleJobs.isEmpty()) {
                // All jobs were closed, completed, or became ineligible during extraction: fail-closed!
                triggerDao.updateState(triggerId, TriggerState.DISCARDED)
                return@withTransaction false
            }

            // A. Address Candidate
            val addr = extractionResult.addressCandidate
            if (addr != null && addr.confidence == "HIGH") {
                val isCurrentClientAddressEmpty = currentClient.city.isNullOrBlank() &&
                    currentClient.street.isNullOrBlank() &&
                    currentClient.buildingNumber.isNullOrBlank()

                if (isCurrentClientAddressEmpty) {
                    if (addr.isCompleteEnough) {
                        // Safe to fill missing client address
                        val updatedClient = currentClient.copy(
                            city = addr.city ?: currentClient.city,
                            district = addr.district ?: currentClient.district,
                            street = addr.street ?: currentClient.street,
                            buildingNumber = addr.buildingNumber ?: currentClient.buildingNumber,
                            unitNumber = addr.unitNumber ?: currentClient.unitNumber,
                            postalCode = addr.postalCode ?: currentClient.postalCode,
                            updatedAt = System.currentTimeMillis()
                        )
                        clientDao.updateClient(updatedClient)

                        // Propagate ONLY to still-valid ACTIVE jobs that have empty address snapshots
                        currentEligibleJobs.forEach { currentJob ->
                            if (currentJob.addressCitySnapshot.isNullOrBlank() && currentJob.addressStreetSnapshot.isNullOrBlank()) {
                                jobDao.updateJob(
                                    currentJob.copy(
                                        addressCitySnapshot = updatedClient.city,
                                        addressDistrictSnapshot = updatedClient.district,
                                        addressStreetSnapshot = updatedClient.street,
                                        addressBuildingSnapshot = updatedClient.buildingNumber,
                                        addressUnitSnapshot = updatedClient.unitNumber,
                                        addressPostalCodeSnapshot = updatedClient.postalCode,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Client already has address -> NEVER overwrite automatically! Create suggestion
                    val isDifferent = addr.street != currentClient.street || addr.buildingNumber != currentClient.buildingNumber
                    if (isDifferent) {
                        val json = JSONObject().apply {
                            put("city", addr.city)
                            put("district", addr.district)
                            put("street", addr.street)
                            put("buildingNumber", addr.buildingNumber)
                            put("unitNumber", addr.unitNumber)
                            put("postalCode", addr.postalCode)
                        }
                        suggestionDao.insertSuggestion(
                            AiSuggestionEntity(
                                id = UUID.randomUUID().toString(),
                                clientId = currentClient.id,
                                type = SuggestionType.ADDRESS_CHANGE,
                                proposedValueJson = json.toString(),
                                sourceSmsAt = trigger.receivedAt,
                                status = SuggestionStatus.PENDING
                            )
                        )
                    }
                }
            }

            // B. Term Candidate
            val term = extractionResult.termCandidate
            if (term != null && term.confidence == "HIGH" && (term.dateEpochDay != null || term.timeMinute != null)) {
                currentEligibleJobs.forEach { currentJob ->
                    val hasExistingTerm = currentJob.preliminaryDateEpochDay != null || currentJob.confirmedStartAt != null
                    if (!hasExistingTerm) {
                        // Empty term -> safe to fill preliminary term (NEVER auto-create Calendar event)
                        jobDao.updateJob(
                            currentJob.copy(
                                preliminaryDateEpochDay = term.dateEpochDay,
                                preliminaryTimeMinute = term.timeMinute,
                                preliminaryTimeQualifier = term.qualifier,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        // Job already has term -> NEVER overwrite automatically! Create suggestion
                        val json = JSONObject().apply {
                            term.dateEpochDay?.let { put("dateEpochDay", it) }
                            term.timeMinute?.let { put("timeMinute", it) }
                            put("qualifier", term.qualifier.name)
                        }
                        suggestionDao.insertSuggestion(
                            AiSuggestionEntity(
                                id = UUID.randomUUID().toString(),
                                clientId = currentClient.id,
                                targetJobId = currentJob.id,
                                type = SuggestionType.TERM_CHANGE,
                                proposedValueJson = json.toString(),
                                sourceSmsAt = trigger.receivedAt,
                                status = SuggestionStatus.PENDING
                            )
                        )
                    }
                }
            }

            // C. Additional contact info
            val contactInfo = extractionResult.additionalContactInfo
            if (!contactInfo.isNullOrBlank()) {
                val json = JSONObject().apply {
                    put("contactInfo", contactInfo)
                }
                suggestionDao.insertSuggestion(
                    AiSuggestionEntity(
                        id = UUID.randomUUID().toString(),
                        clientId = currentClient.id,
                        type = SuggestionType.ADDITIONAL_CONTACT_INFO,
                        proposedValueJson = json.toString(),
                        sourceSmsAt = trigger.receivedAt,
                        status = SuggestionStatus.PENDING
                    )
                )
            }

            // D. Summaries for eligible active jobs
            val currentEligibleJobIdSet = currentEligibleJobs.map { it.id }.toSet()
            extractionResult.jobSummaries.forEach { summaryUpdate ->
                if (currentEligibleJobIdSet.contains(summaryUpdate.jobId)) {
                    val validJob = getStillValidJob(summaryUpdate.jobId)
                    if (validJob != null) {
                        jobDao.updateJob(
                            validJob.copy(
                                smsSummary = summaryUpdate.updatedSummary,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            // E. Update last analyzed SMS on open windows for still-valid jobs
            currentEligibleJobs.forEach { currentJob ->
                val window = windowDao.getOpenWindowForJob(currentJob.id)
                if (window != null && window.endedAt == null) {
                    windowDao.updateLastAnalyzedSms(window.id, trigger.receivedAt)
                }
            }

            triggerDao.updateState(triggerId, TriggerState.PROCESSED)
            true
        }
    }
}
