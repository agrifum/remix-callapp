package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: ClientEntity)

    @Update
    suspend fun updateClient(client: ClientEntity)

    @Delete
    suspend fun deleteClient(client: ClientEntity)

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteClientById(id: String)
}
