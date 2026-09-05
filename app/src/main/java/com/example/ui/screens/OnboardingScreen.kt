package com.example.ui.screens

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.preferences.AppPreferences
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun OnboardingScreen(
    appPreferences: AppPreferences,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    var refresh by remember { mutableIntStateOf(0) }
    var step by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh = refresh + 1 }
    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh = refresh + 1 }

    fun permissionGranted(permission: String) =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun isReady(index: Int): Boolean = when (index) {
        0 -> roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        1 -> permissionGranted(Manifest.permission.READ_PHONE_STATE)
        2 -> Settings.canDrawOverlays(context)
        3 -> android.os.Build.VERSION.SDK_INT < 33 ||
            permissionGranted(Manifest.permission.POST_NOTIFICATIONS)
        4 -> permissionGranted(Manifest.permission.READ_CALL_LOG)
        5 -> permissionGranted(Manifest.permission.READ_CONTACTS)
        else -> true
    }

    LaunchedEffect(refresh) {
        while (step < 6 && isReady(step)) step++
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val labels = listOf(
        "ROLE_CALL_SCREENING",
        "READ_PHONE_STATE",
        "overlay",
        "POST_NOTIFICATIONS",
        "READ_CALL_LOG",
        "READ_CONTACTS"
    )

    Scaffold(modifier = modifier) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Witaj w CallUpp", modifier = Modifier.testTag("onboarding_welcome"))
            Text("Skonfiguruj dostęp krok po kroku. SMS i Kalendarz są proszone dopiero przy użyciu tych funkcji.")
            if (step < labels.size) {
                Text(
                    "Krok ${step + 1} z ${labels.size}: ${labels[step]}",
                    modifier = Modifier.testTag("onboarding_step_label")
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        when (step) {
                            0 -> roleManager?.takeIf {
                                it.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                            }?.let {
                                roleLauncher.launch(it.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                            }
                            1 -> permissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                            2 -> context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                            3 -> if (android.os.Build.VERSION.SDK_INT >= 33) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else refresh++
                            4 -> permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                            5 -> permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    }
                ) { Text("Nadaj: ${labels[step]}") }
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            appPreferences.setOnboardingCompleted(true)
                            onComplete()
                        }
                    }
                ) { Text("Zakończ") }
            }
        }
    }
}
