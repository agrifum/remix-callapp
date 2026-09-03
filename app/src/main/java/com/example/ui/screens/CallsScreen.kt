package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhoneMissed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.entity.ClientEntity
import com.example.data.repository.ClientRepository
import com.example.data.repository.ReengagementRepository
import com.example.system.calls.CallLogRepository
import com.example.ui.components.CallRowItem
import com.example.ui.components.ReengagementDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallsScreen(
    callLogRepository: CallLogRepository,
    clientRepository: ClientRepository,
    reengagementRepository: ReengagementRepository,
    onClientClick: (String) -> Unit,
    onNumberClick: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val clients by clientRepository.allClients.collectAsState(initial = emptyList())
    val pendingReengagements by reengagementRepository.pendingEvents.collectAsState(initial = emptyList())

    var hasPermission by remember {
        mutableStateOf(callLogRepository.hasCallLogPermission())
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            callLogRepository.refresh()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = callLogRepository.hasCallLogPermission()
                hasPermission = granted
                if (granted) {
                    callLogRepository.refresh()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Observed filtered CallLog records (only numbers with a ClientEntity OR at least one non-deleted NoteEntity)
    val callRows by callLogRepository.observeFilteredCallLogs().collectAsState(initial = emptyList())

    // Check if there is an active reengagement event from incoming call or SMS
    val activeReengagement = pendingReengagements.firstOrNull()
    var reengagementClient by remember { mutableStateOf<ClientEntity?>(null) }
    if (activeReengagement != null) {
        val clientMatch = clients.firstOrNull { it.id == activeReengagement.clientId }
        reengagementClient = clientMatch
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Połączenia",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ustawienia")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!hasPermission) {
                // Permission required empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PhoneMissed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = "Wymagany dostęp do rejestru połączeń",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Aby wyświetlać połączenia od Twoich klientów i numerów z notatkami, CallUpp potrzebuje dostępu do rejestru połączeń.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                            }
                        ) {
                            Text("Zezwól na dostęp")
                        }
                    }
                }
            } else if (callRows.isEmpty()) {
                // Filtered empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = "Brak zarejestrowanych połączeń",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Połączenia z numerów należących do Twoich klientów lub numerów z notatkami pojawią się tutaj automatycznie.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(callRows, key = { it.id }) { call ->
                        CallRowItem(
                            call = call,
                            onClick = {
                                if (call.isClient && !call.clientId.isNullOrBlank()) {
                                    onClientClick(call.clientId)
                                } else {
                                    onNumberClick(call.phoneKey)
                                }
                            },
                            onDialClick = {
                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                    data = Uri.parse("tel:${call.phoneKey}")
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }

    // Reengagement prompt if detected
    if (activeReengagement != null) {
        ReengagementDialog(
            event = activeReengagement,
            client = reengagementClient,
            onResumeJob = {
                scope.launch {
                    reengagementRepository.resumeJob(activeReengagement.id, activeReengagement.jobId)
                }
            },
            onNewJob = {
                scope.launch {
                    reengagementRepository.createNewJobFromPrevious(
                        activeReengagement.id,
                        activeReengagement.clientId,
                        activeReengagement.jobId
                    )
                }
            },
            onIgnore = {
                scope.launch {
                    reengagementRepository.ignoreEvent(activeReengagement.id)
                }
            }
        )
    }
}
