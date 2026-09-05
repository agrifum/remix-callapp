package com.example.ui.navigation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenSerializationTest {
    private val json = Json { classDiscriminator = "type" }

    @Test
    fun navKeyObject_isSerializable() {
        val encoded = json.encodeToString(Screen.serializer(), Screen.Calls)
        val decoded = json.decodeFromString(Screen.serializer(), encoded)
        assertEquals(Screen.Calls, decoded)
    }

    @Test
    fun navKeyWithArguments_isSerializable() {
        val encoded = json.encodeToString(Screen.serializer(), Screen.ClientDetail("client-1"))
        val decoded = json.decodeFromString(Screen.serializer(), encoded)
        assertEquals(Screen.ClientDetail("client-1"), decoded)
    }
}
