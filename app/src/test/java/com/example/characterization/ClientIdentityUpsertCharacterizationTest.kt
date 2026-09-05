package com.example.characterization

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.JobStatus
import com.example.data.database.CallUppDatabase
import com.example.data.entity.ClientEntity
import com.example.data.entity.JobEntity
import com.example.data.repository.ClientRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ClientIdentityUpsertCharacterizationTest {
    private lateinit var database: CallUppDatabase
    private lateinit var repository: ClientRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CallUppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ClientRepository(database, database.clientDao(), database.jobDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertClient_duplicateNormalizedNumber_preservesOriginalClientIdAndJobs() = runBlocking {
        val originalClientId = UUID.randomUUID().toString()
        val duplicateClientId = UUID.randomUUID().toString()
        val original = ClientEntity(
            id = originalClientId,
            phoneKey = "+48501234567",
            phoneDisplay = "+48 501 234 567",
            displayName = "Pierwotny klient"
        )
        repository.insertClient(original)

        val jobId = UUID.randomUUID().toString()
        database.jobDao().insertJob(
            JobEntity(
                id = jobId,
                clientId = originalClientId,
                status = JobStatus.ACTIVE
            )
        )

        repository.insertClient(
            ClientEntity(
                id = duplicateClientId,
                phoneKey = "0048 501234567",
                phoneDisplay = "",
                displayName = "Nowsza nazwa"
            )
        )
        repository.insertClient(
            ClientEntity(
                id = UUID.randomUUID().toString(),
                phoneKey = "501234567",
                phoneDisplay = "",
                displayName = "Nowsza nazwa 2"
            )
        )

        val clients = database.clientDao().getAllClientsSync()
        assertEquals(1, clients.size)
        assertEquals(originalClientId, clients.first().id)

        val persistedJob = database.jobDao().getJobByIdSync(jobId)
        assertNotNull(persistedJob)
        assertEquals(originalClientId, persistedJob!!.clientId)
    }
}
