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
     * Process an incoming SMS trigger through the extraction pipeline.
     */
    suspend fun processSmsTrigger(triggerId: String, smsBody: String): Boolean {
        val trigger = triggerDao.getTriggerById(triggerId) ?: return false

        // 1. Check global and client eligibility
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

        // 3. Filter jobs with valid open analysis windows covering the SMS time
        val eligibleJobs = mutableListOf<JobEntity>()
        for (job in activeJobs) {
            val window = windowDao.getOpenWindowForJob(job.id)
            if (window != null && trigger.receivedAt >= window.startedAt) {
                eligibleJobs.add(job)
            }
        }

        if (eligibleJobs.isEmpty()) {
            triggerDao.updateState(triggerId, TriggerState.DISCARDED)
            return false
        }

        // 4. Prepare sanitized input for extraction engine
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

        // 6. Apply field protection rules inside transaction
        database.withTransaction {
            // A. Address Candidate
            val addr = extractionResult.addressCandidate
            if (addr != null && addr.confidence == "HIGH") {
                if (clientAddress.isEmpty) {
                    if (addr.isCompleteEnough) {
                        // Safe to fill missing client address
                        val updatedClient = client.copy(
                            city = addr.city ?: client.city,
                            district = addr.district ?: client.district,
                            street = addr.street ?: client.street,
                            buildingNumber = addr.buildingNumber ?: client.buildingNumber,
                            unitNumber = addr.unitNumber ?: client.unitNumber,
                            postalCode = addr.postalCode ?: client.postalCode,
                            updatedAt = System.currentTimeMillis()
                        )
                        clientDao.updateClient(updatedClient)

                        // Propagate to empty ACTIVE job snapshots
                        eligibleJobs.forEach { job ->
                            if (job.addressCitySnapshot.isNullOrBlank() && job.addressStreetSnapshot.isNullOrBlank()) {
                                jobDao.updateJob(
                                    job.copy(
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
                    val isDifferent = addr.street != client.street || addr.buildingNumber != client.buildingNumber
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
                                clientId = client.id,
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
                eligibleJobs.forEach { job ->
                    val hasExistingTerm = job.preliminaryDateEpochDay != null || job.confirmedStartAt != null
                    if (!hasExistingTerm) {
                        // Empty term -> safe to fill preliminary term (NEVER auto-create Calendar event)
                        jobDao.updateJob(
                            job.copy(
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
                                clientId = client.id,
                                targetJobId = job.id,
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
                        clientId = client.id,
                        type = SuggestionType.ADDITIONAL_CONTACT_INFO,
                        proposedValueJson = json.toString(),
                        sourceSmsAt = trigger.receivedAt,
                        status = SuggestionStatus.PENDING
                    )
                )
            }

            // D. Summaries for eligible active jobs
            val eligibleJobIdSet = eligibleJobs.map { it.id }.toSet()
            extractionResult.jobSummaries.forEach { summaryUpdate ->
                if (eligibleJobIdSet.contains(summaryUpdate.jobId)) {
                    val job = jobDao.getJobByIdSync(summaryUpdate.jobId)
                    if (job != null && job.status == JobStatus.ACTIVE) {
                        jobDao.updateJob(
                            job.copy(
                                smsSummary = summaryUpdate.updatedSummary,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

            // E. Update last analyzed SMS on open windows
            eligibleJobs.forEach { job ->
                val window = windowDao.getOpenWindowForJob(job.id)
                if (window != null) {
                    windowDao.updateLastAnalyzedSms(window.id, trigger.receivedAt)
                }
            }

            triggerDao.updateState(triggerId, TriggerState.PROCESSED)
        }

        return true
    }
}
