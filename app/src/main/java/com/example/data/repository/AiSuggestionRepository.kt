package com.example.data.repository

import androidx.room.withTransaction
import com.example.core.model.SuggestionStatus
import com.example.core.model.SuggestionType
import com.example.data.database.CallUppDatabase
import com.example.data.dao.AiSuggestionDao
import com.example.data.dao.ClientDao
import com.example.data.dao.JobDao
import com.example.data.entity.AiSuggestionEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

import com.example.data.entity.JobEntity
import com.example.system.work.JobCompletionScheduler

class AiSuggestionRepository(
    private val database: CallUppDatabase,
    private val suggestionDao: AiSuggestionDao,
    private val clientDao: ClientDao,
    private val jobDao: JobDao,
    private val scheduler: JobCompletionScheduler? = null
) {

    fun getPendingSuggestionsForJob(jobId: String): Flow<List<AiSuggestionEntity>> =
        suggestionDao.getPendingSuggestionsForJob(jobId)

    fun getPendingSuggestionsForClient(clientId: String): Flow<List<AiSuggestionEntity>> =
        suggestionDao.getPendingSuggestionsForClient(clientId)

    fun getAllPendingSuggestions(): Flow<List<AiSuggestionEntity>> =
        suggestionDao.getAllPendingSuggestions()

    suspend fun insertSuggestion(suggestion: AiSuggestionEntity) {
        suggestionDao.insertSuggestion(suggestion)
    }

    suspend fun ignoreSuggestion(id: String) {
        suggestionDao.resolveSuggestion(id, SuggestionStatus.IGNORED)
    }

    suspend fun acceptAddressSuggestion(suggestionId: String, clientId: String) {
        val suggestion = suggestionDao.getSuggestionByIdSync(suggestionId) ?: return
        if (suggestion.type != SuggestionType.ADDRESS_CHANGE) return

        val json = JSONObject(suggestion.proposedValueJson)
        val city = json.optString("city").takeIf { it.isNotBlank() }
        val district = json.optString("district").takeIf { it.isNotBlank() }
        val street = json.optString("street").takeIf { it.isNotBlank() }
        val building = json.optString("buildingNumber").takeIf { it.isNotBlank() }
        val unit = json.optString("unitNumber").takeIf { it.isNotBlank() }
        val postal = json.optString("postalCode").takeIf { it.isNotBlank() }

        database.withTransaction {
            val client = clientDao.getClientByIdSync(clientId)
            if (client != null) {
                val updatedClient = client.copy(
                    city = city ?: client.city,
                    district = district ?: client.district,
                    street = street ?: client.street,
                    buildingNumber = building ?: client.buildingNumber,
                    unitNumber = unit ?: client.unitNumber,
                    postalCode = postal ?: client.postalCode,
                    updatedAt = System.currentTimeMillis()
                )
                clientDao.updateClient(updatedClient)

                // Propagate to ACTIVE jobs that still have empty addresses
                val activeJobs = jobDao.getActiveJobsForClientSync(clientId)
                activeJobs.forEach { job ->
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
            suggestionDao.resolveSuggestion(suggestionId, SuggestionStatus.ACCEPTED)
        }
    }

    suspend fun acceptTermSuggestion(suggestionId: String, jobId: String) {
        val suggestion = suggestionDao.getSuggestionByIdSync(suggestionId) ?: return
        if (suggestion.type != SuggestionType.TERM_CHANGE) return

        val json = JSONObject(suggestion.proposedValueJson)
        val dateEpochDay = if (json.has("dateEpochDay")) json.optLong("dateEpochDay") else null
        val timeMinute = if (json.has("timeMinute")) json.optInt("timeMinute") else null

        var updatedJob: JobEntity? = null
        database.withTransaction {
            val job = jobDao.getJobByIdSync(jobId)
            if (job != null) {
                val updated = job.copy(
                    preliminaryDateEpochDay = dateEpochDay ?: job.preliminaryDateEpochDay,
                    preliminaryTimeMinute = timeMinute ?: job.preliminaryTimeMinute,
                    updatedAt = System.currentTimeMillis()
                )
                jobDao.updateJob(updated)
                updatedJob = updated
            }
            suggestionDao.resolveSuggestion(suggestionId, SuggestionStatus.ACCEPTED)
        }
        if (updatedJob != null) {
            scheduler?.scheduleCompletion(updatedJob!!)
        }
    }

    suspend fun acceptAdditionalContactInfoSuggestion(suggestionId: String, clientId: String) {
        val suggestion = suggestionDao.getSuggestionByIdSync(suggestionId) ?: return
        if (suggestion.type != SuggestionType.ADDITIONAL_CONTACT_INFO) return

        val json = JSONObject(suggestion.proposedValueJson)
        val info = json.optString("contactInfo")

        database.withTransaction {
            val client = clientDao.getClientByIdSync(clientId)
            if (client != null && info.isNotBlank()) {
                val current = client.additionalInfo
                val newInfo = if (current.isNullOrBlank()) info else "$current\n$info"
                clientDao.updateClient(client.copy(additionalInfo = newInfo, updatedAt = System.currentTimeMillis()))
            }
            suggestionDao.resolveSuggestion(suggestionId, SuggestionStatus.ACCEPTED)
        }
    }
}
