package com.example.system.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionPolicyTest {
    @Test
    fun `clean install requests missing core permissions`() {
        val missing = PermissionPolicy.missingCorePermissions(
            sdkInt = 36,
            granted = emptySet()
        )
        assertTrue("android.permission.READ_PHONE_STATE" in missing)
        assertTrue("android.permission.READ_CALL_LOG" in missing)
        assertTrue("android.permission.READ_CONTACTS" in missing)
        assertTrue("android.permission.POST_NOTIFICATIONS" in missing)
    }

    @Test
    fun `monitoring starts only after phone state permission is granted`() {
        assertFalse(PermissionPolicy.canStartCallMonitoring(emptySet()))
        assertTrue(PermissionPolicy.canStartCallMonitoring(setOf("android.permission.READ_PHONE_STATE")))
    }

    @Test
    fun `notification permission is not requested before android 13`() {
        val missing = PermissionPolicy.missingCorePermissions(sdkInt = 32, granted = emptySet())
        assertFalse("android.permission.POST_NOTIFICATIONS" in missing)
    }
}
