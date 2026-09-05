package com.example.system.permissions

import android.Manifest
import android.os.Build

object PermissionPolicy {
    fun corePermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        add(Manifest.permission.READ_CONTACTS)
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun missingCorePermissions(
        sdkInt: Int = Build.VERSION.SDK_INT,
        granted: Set<String>
    ): List<String> = corePermissions(sdkInt).filterNot(granted::contains)

    fun canStartCallMonitoring(granted: Set<String>): Boolean =
        Manifest.permission.READ_PHONE_STATE in granted
}
