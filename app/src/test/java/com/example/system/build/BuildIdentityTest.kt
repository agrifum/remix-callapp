package com.example.system.build

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildIdentityTest {
    @Test
    fun `display label includes version build and commit`() {
        val identity = BuildIdentity(
            versionName = "1.0.18-test",
            versionCode = 18,
            commitSha = "abcdef1234567890"
        )

        assertEquals("Wersja 1.0.18-test · build 18 · commit abcdef1", identity.displayLabel())
    }

    @Test
    fun `unknown commit remains explicit`() {
        val identity = BuildIdentity("1.0", 1, "unknown")

        assertEquals("Wersja 1.0 · build 1 · commit unknown", identity.displayLabel())
    }
}
