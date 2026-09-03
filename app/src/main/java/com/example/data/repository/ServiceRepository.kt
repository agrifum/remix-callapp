package com.example.data.repository

import com.example.data.dao.ServiceDao
import com.example.data.entity.ServiceEntity
import kotlinx.coroutines.flow.Flow

class ServiceRepository(private val serviceDao: ServiceDao) {

    val allServices: Flow<List<ServiceEntity>> = serviceDao.getAllServices()
    val activeServices: Flow<List<ServiceEntity>> = serviceDao.getActiveServices()

    fun getServiceById(id: String): Flow<ServiceEntity?> = serviceDao.getServiceById(id)

    suspend fun getServiceByIdSync(id: String): ServiceEntity? = serviceDao.getServiceByIdSync(id)

    suspend fun insertService(service: ServiceEntity) {
        serviceDao.insertService(service.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun saveService(service: ServiceEntity) {
        serviceDao.insertService(service.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateService(service: ServiceEntity) {
        serviceDao.updateService(service.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun softDeleteService(id: String) {
        val s = serviceDao.getServiceByIdSync(id)
        if (s != null) {
            serviceDao.updateService(s.copy(isActive = false, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun deleteService(id: String) {
        serviceDao.deleteServiceById(id)
    }
}
