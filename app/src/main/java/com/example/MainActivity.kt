package com.example

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.system.permissions.PermissionPolicy
import com.example.ui.navigation.AppNavHost
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val overlayLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        requestCallScreeningRoleIfNeeded()
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        restartCallMonitoringIfAllowed()
        requestOverlayIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CallUppApplication
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(container = app.container)
                }
            }
        }
        bootstrapPermissions()
    }

    override fun onResume() {
        super.onResume()
        restartCallMonitoringIfAllowed()
    }

    private fun bootstrapPermissions() {
        val missing = PermissionPolicy.missingCorePermissions(
            sdkInt = Build.VERSION.SDK_INT,
            granted = PermissionPolicy.corePermissions().filterTo(mutableSetOf()) {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        )
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            restartCallMonitoringIfAllowed()
            requestOverlayIfNeeded()
        }
    }

    private fun restartCallMonitoringIfAllowed() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            (application as CallUppApplication).ensureCallStateMonitoring()
        }
    }

    private fun requestOverlayIfNeeded() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            runCatching { overlayLauncher.launch(intent) }
                .onFailure { requestCallScreeningRoleIfNeeded() }
        } else {
            requestCallScreeningRoleIfNeeded()
        }
    }

    private fun requestCallScreeningRoleIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val roleManager = getSystemService(RoleManager::class.java) ?: return
        if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
            !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        ) {
            runCatching { roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)) }
        }
    }
}
