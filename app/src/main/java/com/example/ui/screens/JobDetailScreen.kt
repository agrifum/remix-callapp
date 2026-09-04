package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.example.core.model.JobStatus
import com.example.core.time.DateTimeFormatters
import com.example.data.entity.JobEntity
import com.example.data.repository.ClientRepository
import com.example.data.repository.JobRepository
import com.example.ui.theme.StatusGray
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusOrange
import com.example.system.calendar.CalendarManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    jobId: String,
    jobRepository: JobRepository,
    clientRepository: ClientRepository,
    onNavigateBack: () -> Unit,
    onNavigateToClient: (String) -> Unit,
    calendarManager: CalendarManager? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val job by jobRepository.getJobById(jobId).collectAsState(initial = null)
    val client by clientRepository.getClientById(job?.clientId ?: "").collectAsState(initial = null)

    var showEditNotesDialog by remember { mutableStateOf(false) }
    var notesInput by remember { mutableStateOf("") }
    var hasDuplicateConflict by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(job) {
        val j = job
        if (j != null && j.status == JobStatus.ACTIVE && j.preliminaryDateEpochDay != null) {
            hasDuplicateConflict = jobRepository.checkHasDuplicateActiveTerm(
                clientId = j.clientId,
                currentJobId = j.id,
                dateEpochDay = j.preliminaryDateEpochDay,
                timeMinute = j.preliminaryTimeMinute
            )
        } else {
            hasDuplicateConflict = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(job?.serviceNameSnapshot ?: "Szczegóły zlecenia") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    if (job != null) {
                        IconButton(onClick = {
                            scope.launch {
                                jobRepository.softDeleteJob(job!!.id)
                                onNavigateBack()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Usuń zlecenie")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        job?.let { j ->
            val statusColor = when (j.status) {
                JobStatus.ACTIVE -> StatusGreen
                JobStatus.COMPLETED -> StatusOrange
                JobStatus.CLOSED -> StatusGray
            }
            val statusText = when (j.status) {
                JobStatus.ACTIVE -> "Aktywne"
                JobStatus.COMPLETED -> "Zakończone"
                JobStatus.CLOSED -> "Zamknięte"
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Main Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = j.serviceNameSnapshot ?: "Zlecenie",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = statusColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = statusText,
                                    color = statusColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (client != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Klient: ${client!!.displayName}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = client!!.phoneDisplay,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (j.priceMinor != null && j.priceMinor > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Cena: ${DateTimeFormatters.formatMoney(j.priceMinor)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // Term & Schedule Card
                val term = DateTimeFormatters.formatJobTerm(
                    j.preliminaryDateEpochDay,
                    j.preliminaryTimeMinute,
                    j.preliminaryTimeQualifier,
                    j.confirmedStartAt
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Termin realizacji",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (term.isNotBlank()) term else "Brak ustalonego terminu",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (hasDuplicateConflict) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "⚠️ Klient ma inne aktywne zlecenie w tym samym terminie.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (j.calendarEventId != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "✓ Dodano do kalendarza Android",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium
                            )
                        } else if (j.status == JobStatus.ACTIVE && (j.confirmedStartAt != null || j.preliminaryDateEpochDay != null)) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        val anchor = JobRepository.calculateCompletionAnchor(j)
                                        val confirmedJob = if (j.confirmedStartAt == null && anchor != null) {
                                            j.copy(confirmedStartAt = anchor)
                                        } else {
                                            j
                                        }
                                        var eventId: Long? = null
                                        if (calendarManager != null) {
                                            try {
                                                eventId = calendarManager.createEvent(confirmedJob, client)
                                            } catch (_: Exception) {}
                                        }
                                        jobRepository.updateJob(
                                            confirmedJob.copy(
                                                calendarEventId = eventId ?: confirmedJob.calendarEventId
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Potwierdź termin i dodaj do kalendarza")
                            }
                        }
                    }
                }

                // Address & Navigation Card
                val fullAddress = listOfNotNull(
                    j.addressStreetSnapshot,
                    j.addressBuildingSnapshot,
                    j.addressCitySnapshot
                ).joinToString(" ").trim()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Adres zlecenia",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (fullAddress.isNotBlank()) fullAddress else "Brak podanego adresu",
                            style = MaterialTheme.typography.bodyLarge
                        )

                        if (fullAddress.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(fullAddress))
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                        setPackage("com.google.android.apps.maps")
                                    }
                                    if (mapIntent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(mapIntent)
                                    } else {
                                        val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(fullAddress)))
                                        context.startActivity(geoIntent)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Nawiguj do adresu (Google Maps)")
                            }
                        }
                    }
                }

                // Notes Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Notatki i ustalenia",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = {
                                notesInput = j.manualNotes ?: ""
                                showEditNotesDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edytuj notatki")
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = j.manualNotes ?: "Brak notatek do tego zlecenia.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (j.manualNotes != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!j.smsSummary.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Podsumowanie ustaleń SMS: ${j.smsSummary}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                // State Transitions: Zakończ / Zamknij / Wznów
                Spacer(modifier = Modifier.height(4.dp))
                when (j.status) {
                    JobStatus.ACTIVE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        jobRepository.completeJob(j.id)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Oznacz jako wykonane")
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        jobRepository.closeJob(j.id)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Zamknij zlecenie")
                            }
                        }
                    }

                    JobStatus.COMPLETED -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        jobRepository.reopenJob(j.id)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Wznów zlecenie")
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        jobRepository.closeJob(j.id)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Zamknij (zakończone)")
                            }
                        }
                    }

                    JobStatus.CLOSED -> {
                        Button(
                            onClick = {
                                scope.launch {
                                    jobRepository.reopenJob(j.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Wznów zlecenie jako Aktywne")
                        }
                    }
                }
            }
        }
    }

    if (showEditNotesDialog && job != null) {
        AlertDialog(
            onDismissRequest = { showEditNotesDialog = false },
            title = { Text("Edycja notatek zlecenia") },
            text = {
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Notatki zlecenia") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            jobRepository.updateJob(job!!.copy(manualNotes = notesInput.ifBlank { null }))
                            showEditNotesDialog = false
                        }
                    }
                ) {
                    Text("Zapisz")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNotesDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}
