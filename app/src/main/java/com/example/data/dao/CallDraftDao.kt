package com.example.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import com.example.data.entity.CallDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDraftDao {

    @Query("SELECT * FROM call_drafts WHERE callSessionId = :callSessionId LIMIT 1")
    fun getDraft(callSessionId: String): Flow<CallDraftEntity?>

    @Query("SELECT * FROM call_drafts WHERE callSessionId = :callSessionId LIMIT 1")
    suspend fun getDraftSync(callSessionId: String): CallDraftEntity?

    @Query("SELECT * FROM call_drafts WHERE phoneKey = :phoneKey ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestDraftForPhone(phoneKey: String): CallDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: CallDraftEntity)

    @Query("DELETE FROM call_drafts WHERE callSessionId = :callSessionId")
    suspend fun deleteDraft(callSessionId: String)
}
