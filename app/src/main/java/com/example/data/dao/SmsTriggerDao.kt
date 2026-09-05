package com.example.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.example.core.model.TriggerState
import com.example.data.entity.SmsTriggerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsTriggerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrigger(trigger: SmsTriggerEntity)

    @Update
    suspend fun updateTrigger(trigger: SmsTriggerEntity)

    @Query("SELECT * FROM sms_triggers WHERE state = 'PENDING' ORDER BY receivedAt ASC")
    suspend fun getPendingTriggers(): List<SmsTriggerEntity>

    @Query("SELECT * FROM sms_triggers WHERE state IN ('PENDING', 'FAILED') ORDER BY receivedAt ASC")
    suspend fun getRecoverableTriggers(): List<SmsTriggerEntity>

    @Query("SELECT * FROM sms_triggers WHERE state = 'PENDING' ORDER BY receivedAt ASC")
    fun observePendingTriggers(): Flow<List<SmsTriggerEntity>>

    @Query("SELECT * FROM sms_triggers WHERE id = :id LIMIT 1")
    suspend fun getTriggerById(id: String): SmsTriggerEntity?

    @Query("SELECT * FROM sms_triggers WHERE clientId = :clientId ORDER BY receivedAt DESC LIMIT 1")
    suspend fun getLatestTriggerForClient(clientId: String): SmsTriggerEntity?

    @Query("UPDATE sms_triggers SET state = :state WHERE id = :id")
    suspend fun updateState(id: String, state: TriggerState)

    @Query("UPDATE sms_triggers SET attemptCount = attemptCount + 1 WHERE id = :id")
    suspend fun incrementAttemptCount(id: String)

    @Query("UPDATE sms_triggers SET state = :state, attemptCount = attemptCount + 1 WHERE id = :id")
    suspend fun updateStateAndIncrementAttempt(id: String, state: TriggerState)
}
