package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.model.ReengagementStatus
import com.example.data.entity.ReengagementEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReengagementEventDao {

    @Query("SELECT * FROM reengagement_events WHERE status = 'PENDING' ORDER BY occurredAt DESC")
    fun getPendingEvents(): Flow<List<ReengagementEventEntity>>

    @Query("SELECT * FROM reengagement_events WHERE clientId = :clientId AND status = 'PENDING' LIMIT 1")
    suspend fun getPendingEventForClient(clientId: String): ReengagementEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: ReengagementEventEntity)

    @Update
    suspend fun updateEvent(event: ReengagementEventEntity)

    @Query("UPDATE reengagement_events SET status = :status WHERE id = :id")
    suspend fun updateEventStatus(id: String, status: ReengagementStatus)

    @Query("UPDATE reengagement_events SET status = :status WHERE clientId = :clientId AND status = 'PENDING'")
    suspend fun updatePendingEventsForClient(clientId: String, status: ReengagementStatus)
}
