package com.example.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.CallUppApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            (context.applicationContext as? CallUppApplication)?.ensureCallStateMonitoring()
        }
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh++ }

    fun granted(permission: String): Boolean {
        refresh
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun openAppSettings() {
        settingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Uprawnienia") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Dostępy wymagane przez funkcje CallUpp", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            PermissionRow("Telefon", granted(Manifest.permission.READ_PHONE_STATE)) {
                if (granted(Manifest.permission.READ_PHONE_STATE)) openAppSettings() else runtimeLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            }
            PermissionRow("Historia połączeń", granted(Manifest.permission.READ_CALL_LOG)) {
                if (granted(Manifest.permission.READ_CALL_LOG)) openAppSettings() else runtimeLauncher.launch(Manifest.permission.READ_CALL_LOG)
            }
            PermissionRow("Kontakty", granted(Manifest.permission.READ_CONTACTS)) {
                if (granted(Manifest.permission.READ_CONTACTS)) openAppSettings() else runtimeLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow("Powiadomienia", granted(Manifest.permission.POST_NOTIFICATIONS)) {
                    if (granted(Manifest.permission.POST_NOTIFICATIONS)) openAppSettings() else runtimeLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            PermissionRow("Wyświetlanie nad innymi aplikacjami", Settings.canDrawOverlays(context)) {
                settingsLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                val roleHeld = roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true && roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
                PermissionRow("Identyfikacja połączeń", roleHeld) {
                    if (roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true && !roleHeld) {
                        roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                    } else {
                        runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, enabled: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Medium)
                Text(if (enabled) "Włączone" else "Wyłączone", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = enabled, onCheckedChange = { onClick() })
        }
    }
}
