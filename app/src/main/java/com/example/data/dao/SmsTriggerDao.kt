package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.model.TriggerState
import com.example.data.entity.SmsTriggerEntity

@Dao
interface SmsTriggerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrigger(trigger: SmsTriggerEntity)

    @Update
    suspend fun updateTrigger(trigger: SmsTriggerEntity)

    @Query("SELECT * FROM sms_triggers WHERE state = 'PENDING' ORDER BY receivedAt ASC")
    suspend fun getPendingTriggers(): List<SmsTriggerEntity>

    @Query("SELECT * FROM sms_triggers WHERE id = :id LIMIT 1")
    suspend fun getTriggerById(id: String): SmsTriggerEntity?

    @Query("UPDATE sms_triggers SET state = :state, attemptCount = attemptCount + 1 WHERE id = :id")
    suspend fun updateState(id: String, state: TriggerState)
}
