package com.example.data.repository

import androidx.room3.withWriteTransaction
import com.example.core.model.JobStatus
import com.example.core.phone.PhoneNumberNormalizer
import com.example.data.database.CallUppDatabase
import com.example.data.dao.ClientDao
import com.example.data.dao.JobDao
import com.example.data.entity.ClientEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow

class ClientRepository(
    private val database: CallUppDatabase,
    private val clientDao: ClientDao,
    private val jobDao: JobDao
) {
    val allClients: Flow<List<ClientEntity>> = clientDao.getAllClients()

    fun getClientById(id: String): Flow<ClientEntity?> = clientDao.getClientById(id)

    suspend fun getClientByIdSync(id: String): ClientEntity? = clientDao.getClientByIdSync(id)

    fun getClientByPhoneKey(phoneKey: String): Flow<ClientEntity?> {
        val normalized = PhoneNumberNormalizer.normalizeKey(phoneKey)
        return clientDao.getClientByPhoneKey(normalized)
    }

    suspend fun getClientByPhoneKeySync(phoneKey: String): ClientEntity? {
        val normalized = PhoneNumberNormalizer.normalizeKey(phoneKey)
        return clientDao.getClientByPhoneKeySync(normalized)
    }

    suspend fun insertClient(client: ClientEntity) {
        val normalizedKey = PhoneNumberNormalizer.normalizeKey(client.phoneKey)
        val display = PhoneNumberNormalizer.formatDisplay(normalizedKey)
        val now = System.currentTimeMillis()
        val candidate = client.copy(phoneKey = normalizedKey, phoneDisplay = display, updatedAt = now)
        val insertResult = clientDao.insertClient(candidate)
        if (insertResult == -1L) {
            val existing = clientDao.getClientByPhoneKeySync(normalizedKey) ?: return
            clientDao.updateClient(
                existing.copy(
                    displayName = candidate.displayName.ifBlank { existing.displayName },
                    nameSource = candidate.nameSource,
                    smsAnalysisMode = candidate.smsAnalysisMode,
                    city = candidate.city ?: existing.city,
                    district = candidate.district ?: existing.district,
                    street = candidate.street ?: existing.street,
                    buildingNumber = candidate.buildingNumber ?: existing.buildingNumber,
                    unitNumber = candidate.unitNumber ?: existing.unitNumber,
                    postalCode = candidate.postalCode ?: existing.postalCode,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun updateClient(client: ClientEntity) {
        val normalizedKey = PhoneNumberNormalizer.normalizeKey(client.phoneKey)
        val display = PhoneNumberNormalizer.formatDisplay(normalizedKey)
        val updatedAt = System.currentTimeMillis()
        val updatedClient = client.copy(phoneKey = normalizedKey, phoneDisplay = display, updatedAt = updatedAt)
        database.withWriteTransaction {
            clientDao.updateClient(updatedClient)
            jobDao.updateActiveJobAddressSnapshotsForClient(
                clientId = updatedClient.id,
                city = updatedClient.city,
                district = updatedClient.district,
                street = updatedClient.street,
                buildingNumber = updatedClient.buildingNumber,
                unitNumber = updatedClient.unitNumber,
                postalCode = updatedClient.postalCode,
                updatedAt = updatedAt
            )
        }
    }

    suspend fun deleteClient(client: ClientEntity) {
        clientDao.deleteClient(client)
    }

    suspend fun deleteClientById(id: String) {
        clientDao.deleteClientById(id)
    }

    /**
     * Section 11: Derived tags from Client data and ACTIVE jobs.
     * city, district, street, services of ACTIVE Jobs.
     */
    fun getDerivedTags(client: ClientEntity): Flow<List<String>> {
        return jobDao.getActiveJobsForClient(client.id).combine(flow { emit(client) }) { activeJobs, c ->
            val tags = mutableListOf<String>()
            c.city?.takeIf { it.isNotBlank() }?.let { tags.add(it.trim()) }
            c.district?.takeIf { it.isNotBlank() }?.let { tags.add(it.trim()) }
            c.street?.takeIf { it.isNotBlank() }?.let { tags.add(it.trim()) }
            activeJobs.forEach { job ->
                job.serviceNameSnapshot?.takeIf { it.isNotBlank() }?.let { serviceName ->
                    if (!tags.contains(serviceName.trim())) {
                        tags.add(serviceName.trim())
                    }
                }
            }
            tags
        }
    }
}
