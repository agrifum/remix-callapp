package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import com.example.core.model.NameSource
import com.example.core.model.SmsAnalysisMode
import com.example.data.entity.ClientEntity
import com.example.data.repository.ClientRepository
import com.example.data.repository.JobRepository
import com.example.data.repository.NoteRepository
import com.example.ui.components.JobCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClientDetailScreen(
    clientId: String,
    clientRepository: ClientRepository,
    jobRepository: JobRepository,
    noteRepository: NoteRepository,
    onNavigateBack: () -> Unit,
    onNavigateToJob: (String) -> Unit,
    onNewJobForClient: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client by clientRepository.getClientById(clientId).collectAsState(initial = null)
    val jobs by jobRepository.getAllJobsForClient(clientId).collectAsState(initial = emptyList())

    val activeJobs = remember(jobs) { jobs.filter { it.status == JobStatus.ACTIVE && !it.isArchived } }
    val historyJobs = remember(jobs) { jobs.filter { it.status != JobStatus.ACTIVE && !it.isArchived } }

    var showEditDialog by remember { mutableStateOf(false) }
    var editDisplayName by remember { mutableStateOf("") }
    var editFirstName by remember { mutableStateOf("") }
    var editLastName by remember { mutableStateOf("") }
    var editNip by remember { mutableStateOf("") }
    var editCity by remember { mutableStateOf("") }
    var editDistrict by remember { mutableStateOf("") }
    var editStreet by remember { mutableStateOf("") }
    var editBuildingNumber by remember { mutableStateOf("") }
    var editUnitNumber by remember { mutableStateOf("") }
    var editPostalCode by remember { mutableStateOf("") }
    var editAdditionalInfo by remember { mutableStateOf("") }
    var editSmsMode by remember { mutableStateOf(SmsAnalysisMode.INHERIT) }

    var smsDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(client?.displayName ?: "Klient") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    if (client != null) {
                        IconButton(onClick = {
                            val c = client!!
                            editDisplayName = c.displayName
                            editFirstName = c.firstName ?: ""
                            editLastName = c.lastName ?: ""
                            editNip = c.nip ?: ""
                            editCity = c.city ?: ""
                            editDistrict = c.district ?: ""
                            editStreet = c.street ?: ""
                            editBuildingNumber = c.buildingNumber ?: ""
                            editUnitNumber = c.unitNumber ?: ""
                            editPostalCode = c.postalCode ?: ""
                            editAdditionalInfo = c.additionalInfo ?: ""
                            editSmsMode = c.smsAnalysisMode
                            showEditDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edytuj dane klienta")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        client?.let { c ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Client info card
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = c.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = c.phoneDisplay,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Active Job Count Status (§6)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (activeJobs.isNotEmpty()) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "Aktywne: ${activeJobs.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeJobs.isNotEmpty()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Automatic Tags (§7): miasto, dzielnica, ulica, usługi aktywnych zleceń
                        val tags = buildList {
                            c.city?.ifBlank { null }?.let { add(it) }
                            c.district?.ifBlank { null }?.let { add(it) }
                            c.street?.ifBlank { null }?.let { add(it) }
                            activeJobs.mapNotNull { it.serviceNameSnapshot?.ifBlank { null } }.distinct().forEach { add(it) }
                        }

                        if (tags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                tags.forEach { tag ->
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }

                        val addressParts = listOfNotNull(c.postalCode, c.city, c.street, c.buildingNumber).filter { it.isNotBlank() }
                        if (addressParts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = addressParts.joinToString(", "),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        if (!c.nip.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "NIP: ${c.nip}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // SMS Analysis Mode (§6, §20)
                        Spacer(modifier = Modifier.height(6.dp))
                        val smsModeText = when (c.smsAnalysisMode) {
                            SmsAnalysisMode.INHERIT -> "Domyślnie"
                            SmsAnalysisMode.ENABLED -> "Włączona"
                            SmsAnalysisMode.DISABLED -> "Wyłączona"
                        }
                        Text(
                            text = "Analiza SMS: $smsModeText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )

                        if (!c.additionalInfo.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Dodatkowe: ${c.additionalInfo}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Quick Actions (§6): Zadzwoń, SMS, Nawiguj
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${c.phoneKey}")
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Zadzwoń")
                            }

                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("smsto:${c.phoneKey}")
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SMS")
                            }

                            val fullAddr = addressParts.joinToString(" ").trim()
                            if (fullAddr.isNotBlank()) {
                                OutlinedButton(
                                    onClick = {
                                        val gmmIntentUri = Uri.parse("google.navigation:q=" + Uri.encode(fullAddr))
                                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                            setPackage("com.google.android.apps.maps")
                                        }
                                        if (mapIntent.resolveActivity(context.packageManager) != null) {
                                            context.startActivity(mapIntent)
                                        } else {
                                            val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(fullAddr)))
                                            context.startActivity(geoIntent)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Nawiguj")
                                }
                            }
                        }
                    }
                }

                // Section: Aktywne zlecenia
                Text(
                    text = "Aktywne zlecenia (${activeJobs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (activeJobs.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Brak aktywnych zleceń dla tego klienta.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    activeJobs.forEach { job ->
                        JobCard(
                            job = job,
                            clientName = null,
                            onClick = { onNavigateToJob(job.id) }
                        )
                    }
                }

                // Section: Historia zleceń (Zakończone / Zamknięte)
                if (historyJobs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Historia zleceń (${historyJobs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    historyJobs.forEach { job ->
                        JobCard(
                            job = job,
                            clientName = null,
                            onClick = { onNavigateToJob(job.id) }
                        )
                    }
                }
            }
        }
    }

    if (showEditDialog && client != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edycja danych klienta") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editDisplayName,
                        onValueChange = { editDisplayName = it },
                        label = { Text("Nazwa wyświetlana") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editFirstName,
                            onValueChange = { editFirstName = it },
                            label = { Text("Imię") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editLastName,
                            onValueChange = { editLastName = it },
                            label = { Text("Nazwisko") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = editNip,
                        onValueChange = { editNip = it },
                        label = { Text("NIP") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editCity,
                            onValueChange = { editCity = it },
                            label = { Text("Miejscowość") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editDistrict,
                            onValueChange = { editDistrict = it },
                            label = { Text("Dzielnica") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = editStreet,
                        onValueChange = { editStreet = it },
                        label = { Text("Ulica") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editBuildingNumber,
                            onValueChange = { editBuildingNumber = it },
                            label = { Text("Nr budynku") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editUnitNumber,
                            onValueChange = { editUnitNumber = it },
                            label = { Text("Nr lokalu") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = editPostalCode,
                        onValueChange = { editPostalCode = it },
                        label = { Text("Kod pocztowy") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // SMS Analysis mode selector
                    ExposedDropdownMenuBox(
                        expanded = smsDropdownExpanded,
                        onExpandedChange = { smsDropdownExpanded = !smsDropdownExpanded }
                    ) {
                        val currentModeLabel = when (editSmsMode) {
                            SmsAnalysisMode.INHERIT -> "Domyślnie"
                            SmsAnalysisMode.ENABLED -> "Włączona"
                            SmsAnalysisMode.DISABLED -> "Wyłączona"
                        }
                        OutlinedTextField(
                            value = currentModeLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Analiza SMS") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = smsDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = smsDropdownExpanded,
                            onDismissRequest = { smsDropdownExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Domyślnie (INHERIT)") },
                                onClick = {
                                    editSmsMode = SmsAnalysisMode.INHERIT
                                    smsDropdownExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Włączona (ENABLED)") },
                                onClick = {
                                    editSmsMode = SmsAnalysisMode.ENABLED
                                    smsDropdownExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Wyłączona (DISABLED)") },
                                onClick = {
                                    editSmsMode = SmsAnalysisMode.DISABLED
                                    smsDropdownExpanded = false
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = editAdditionalInfo,
                        onValueChange = { editAdditionalInfo = it },
                        label = { Text("Dodatkowe informacje") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val c = client!!
                        scope.launch {
                            clientRepository.updateClient(
                                c.copy(
                                    displayName = editDisplayName.ifBlank { c.displayName },
                                    nameSource = NameSource.MANUAL,
                                    firstName = editFirstName.ifBlank { null },
                                    lastName = editLastName.ifBlank { null },
                                    nip = editNip.ifBlank { null },
                                    city = editCity.ifBlank { null },
                                    district = editDistrict.ifBlank { null },
                                    street = editStreet.ifBlank { null },
                                    buildingNumber = editBuildingNumber.ifBlank { null },
                                    unitNumber = editUnitNumber.ifBlank { null },
                                    postalCode = editPostalCode.ifBlank { null },
                                    smsAnalysisMode = editSmsMode,
                                    additionalInfo = editAdditionalInfo.ifBlank { null },
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            showEditDialog = false
                        }
                    }
                ) {
                    Text("Zapisz")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}