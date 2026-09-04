package com.example.ai

import com.example.ai.model.ClientAddressInput
import com.example.ai.model.SmsExtractionInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FirebaseSmsExtractionEngineTest {

    @Test
    fun testExtractionFailsClosedWithoutFirebaseConfig() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val engine = FirebaseSmsExtractionEngine(context)

        val input = SmsExtractionInput(
            smsBody = "Dzień dobry, jutro o 14:00 ul. Lipowa 5 Warszawa",
            receivedTimestamp = System.currentTimeMillis(),
            localDateTime = "2026-09-05T10:00:00",
            timezone = "Europe/Warsaw",
            clientAddress = ClientAddressInput(),
            activeJobIds = emptyList(),
            activeJobTerms = emptyMap(),
            activeJobSummaries = emptyMap()
        )

        // Without Google Services runtime JSON configuration, the engine safely fails closed and returns null
        val result = engine.extract(input)
        assertNull("Expected null result when Firebase is not configured", result)
    }

    @Test
    fun testFakeEngineExtractsDeterministically() = runBlocking {
        val fakeEngine = FakeSmsExtractionEngine()
        val input = SmsExtractionInput(
            smsBody = "Dzień dobry, jutro o 14:00 ul. Lipowa 5 Warszawa",
            receivedTimestamp = System.currentTimeMillis(),
            localDateTime = "2026-09-05T10:00:00",
            timezone = "Europe/Warsaw",
            clientAddress = ClientAddressInput(),
            activeJobIds = emptyList(),
            activeJobTerms = emptyMap(),
            activeJobSummaries = emptyMap()
        )

        val result = fakeEngine.extract(input)
        assertNotNull(result)
        assertNotNull(result?.addressCandidate)
    }
}
