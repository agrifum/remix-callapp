package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.example.core.model.CallDirection
import com.example.data.entity.ClientEntity
import com.example.data.entity.ServiceEntity
import com.example.data.repository.CallDraftRepository
import com.example.data.repository.ClientRepository
import com.example.data.repository.OverlayCommitRequest
import com.example.data.repository.ServiceRepository
import com.example.ui.theme.StatusGreen
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SimulatorScreen(
    clientRepository: ClientRepository,
    serviceRepository: ServiceRepository,
    callDraftRepository: CallDraftRepository,
    onNavigateToJobs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val services by serviceRepository.activeServices.collectAsState(initial = emptyList())

    var simulatedPhone by remember { mutableStateOf("601 234 567") }
    var simulatedClientName by remember { mutableStateOf("Jan Kowalski (Przykładowy)") }
    var simulatedNote by remember { mutableStateOf("Wymiana zaworu i uszczelnienie instalacji") }
    var createJobChecked by remember { mutableStateOf(true) }
    var markClientChecked by remember { mutableStateOf(true) }
    var createOpenTaskChecked by remember { mutableStateOf(false) }
    var selectedServiceId by remember { mutableStateOf<String?>(null) }
    var executionResult by remember { mutableStateOf<String?>(null) }

    val canDrawOverlays = remember {
        Settings.canDrawOverlays(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overlay Permission Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (canDrawOverlays) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (canDrawOverlays) Icons.Default.Check else Icons.Default.Security,
                    contentDescription = null,
                    tint = if (canDrawOverlays) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (canDrawOverlays) "Uprawnienie nakładki: Aktywne" else "Brak uprawnienia nakładki",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (canDrawOverlays) "System ma zgodę na pokazywanie okna na wierzchu podczas rozmów."
                        else "Aby nakładka pojawiała się automatycznie podczas rozmowy w tle, włącz uprawnienie w systemie.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (!canDrawOverlays) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Włącz")
                    }
                }
            }
        }

        // Direct Simulation Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Symulator zapisu notatki i zlecenia",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Przetestuj proces, który normalnie dzieje się w trakcie rozmowy telefonicznej:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = simulatedPhone,
                    onValueChange = { simulatedPhone = it },
                    label = { Text("Numer rozmówcy") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = simulatedClientName,
                    onValueChange = { simulatedClientName = it },
                    label = { Text("Imię i nazwisko klienta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = simulatedNote,
                    onValueChange = { simulatedNote = it },
                    label = { Text("Treść notatki z rozmowy") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Oznacz jako Klienta", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = markClientChecked, onCheckedChange = { markClientChecked = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Utwórz od razu aktywne Zlecenie", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = createJobChecked, onCheckedChange = { createJobChecked = it })
                }

                if (createJobChecked && services.isNotEmpty()) {
                    Text(
                        text = "Wybierz usługę:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        services.take(3).forEach { service ->
                            FilterChip(
                                selected = selectedServiceId == service.id,
                                onClick = {
                                    selectedServiceId = if (selectedServiceId == service.id) null else service.id
                                },
                                label = { Text(service.name) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Dodaj do Otwartych Zadań", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = createOpenTaskChecked, onCheckedChange = { createOpenTaskChecked = it })
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            val req = OverlayCommitRequest(
                                callSessionId = UUID.randomUUID().toString(),
                                phone = simulatedPhone,
                                noteText = simulatedNote,
                                markAsClient = markClientChecked,
                                clientDisplayName = simulatedClientName,
                                createJob = createJobChecked,
                                serviceId = selectedServiceId,
                                createOpenTask = createOpenTaskChecked,
                                callDirection = CallDirection.INCOMING,
                                callTimestamp = System.currentTimeMillis()
                            )
                            callDraftRepository.commitOverlaySession(req)
                            executionResult = "Pomyślnie przetworzono! Utworzono klienta, notatkę i zlecenie."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zapisz rozmowę (Symulacja)")
                }

                if (executionResult != null) {
                    Surface(
                        color = StatusGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = executionResult!!,
                                color = StatusGreen,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = onNavigateToJobs,
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
                            ) {
                                Text("Przejdź do listy zleceń")
                            }
                        }
                    }
                }
            }
        }
    }
}
