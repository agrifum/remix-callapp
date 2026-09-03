package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.core.model.SuggestionStatus
import com.example.data.entity.AiSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiSuggestionDao {

    @Query("SELECT * FROM ai_suggestions WHERE clientId = :clientId AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingSuggestionsForClient(clientId: String): Flow<List<AiSuggestionEntity>>

    @Query("SELECT * FROM ai_suggestions WHERE targetJobId = :jobId AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingSuggestionsForJob(jobId: String): Flow<List<AiSuggestionEntity>>

    @Query("SELECT * FROM ai_suggestions WHERE targetJobId = :jobId AND status = 'PENDING' ORDER BY createdAt DESC")
    suspend fun getPendingSuggestionsForJobSync(jobId: String): List<AiSuggestionEntity>

    @Query("SELECT * FROM ai_suggestions WHERE status = 'PENDING' ORDER BY createdAt DESC")
    fun getAllPendingSuggestions(): Flow<List<AiSuggestionEntity>>

    @Query("SELECT * FROM ai_suggestions WHERE id = :id LIMIT 1")
    fun getSuggestionById(id: String): Flow<AiSuggestionEntity?>

    @Query("SELECT * FROM ai_suggestions WHERE id = :id LIMIT 1")
    suspend fun getSuggestionByIdSync(id: String): AiSuggestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestion(suggestion: AiSuggestionEntity)

    @Update
    suspend fun updateSuggestion(suggestion: AiSuggestionEntity)

    @Query("UPDATE ai_suggestions SET status = :status, resolvedAt = :resolvedAt WHERE id = :id")
    suspend fun resolveSuggestion(id: String, status: SuggestionStatus, resolvedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM ai_suggestions WHERE targetJobId = :jobId")
    suspend fun deleteSuggestionsForJob(jobId: String)
}
