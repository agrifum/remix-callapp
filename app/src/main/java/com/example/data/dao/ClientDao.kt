package com.example.data.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.example.data.entity.ClientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {

    @Query("SELECT * FROM clients ORDER BY displayName COLLATE NOCASE ASC")
    fun getAllClients(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients ORDER BY displayName COLLATE NOCASE ASC")
    suspend fun getAllClientsSync(): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    fun getClientById(id: String): Flow<ClientEntity?>

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getClientByIdSync(id: String): ClientEntity?

    @Query("SELECT * FROM clients WHERE phoneKey = :phoneKey LIMIT 1")
    fun getClientByPhoneKey(phoneKey: String): Flow<ClientEntity?>

    @Query("SELECT * FROM clients WHERE phoneKey = :phoneKey LIMIT 1")
    suspend fun getClientByPhoneKeySync(phoneKey: String): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertClient(client: ClientEntity): Long

    @Update
    suspend fun updateClient(client: ClientEntity)

    @Delete
    suspend fun deleteClient(client: ClientEntity)

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteClientById(id: String)
}
