package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.core.model.JobStatus
import com.example.data.entity.ClientEntity
import com.example.data.entity.JobEntity
import com.example.data.repository.ClientRepository
import com.example.data.repository.JobRepository
import com.example.data.repository.ServiceRepository
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewJobScreen(
    jobRepository: JobRepository,
    clientRepository: ClientRepository,
    serviceRepository: ServiceRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val clients by clientRepository.allClients.collectAsState(initial = emptyList())
    val services by serviceRepository.activeServices.collectAsState(initial = emptyList())

    var selectedClientId by remember { mutableStateOf<String?>(null) }
    var selectedServiceId by remember { mutableStateOf<String?>(null) }
    var notes by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }

    var clientDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nowe Zlecenie") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Client selection
            ExposedDropdownMenuBox(
                expanded = clientDropdownExpanded,
                onExpandedChange = { clientDropdownExpanded = !clientDropdownExpanded }
            ) {
                val selectedClient = clients.find { it.id == selectedClientId }
                OutlinedTextField(
                    value = selectedClient?.displayName ?: "Wybierz klienta",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Klient") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = clientDropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = clientDropdownExpanded,
                    onDismissRequest = { clientDropdownExpanded = false }
                ) {
                    clients.forEach { client ->
                        DropdownMenuItem(
                            text = { Text("${client.displayName} (${client.phoneDisplay})") },
                            onClick = {
                                selectedClientId = client.id
                                client.city?.let { city = it }
                                client.street?.let { street = it }
                                clientDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Services
            if (services.isNotEmpty()) {
                Text("Wybierz typ usługi:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    services.forEach { s ->
                        FilterChip(
                            selected = selectedServiceId == s.id,
                            onClick = {
                                if (selectedServiceId == s.id) {
                                    selectedServiceId = null
                                } else {
                                    selectedServiceId = s.id
                                    s.defaultPriceMinor?.let { priceText = (it / 100).toString() }
                                }
                            },
                            label = { Text(s.name) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Opis zlecenia / Notatka") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("Miejscowość") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = street,
                    onValueChange = { street = it },
                    label = { Text("Ulica i numer") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Wycena w PLN (opcjonalnie)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val clientId = selectedClientId
                    if (clientId != null) {
                        scope.launch {
                            val selectedService = services.find { it.id == selectedServiceId }
                            val priceMinor = priceText.toLongOrNull()?.times(100)
                            val job = JobEntity(
                                clientId = clientId,
                                serviceId = selectedServiceId,
                                serviceNameSnapshot = selectedService?.name ?: "Zlecenie",
                                priceMinor = priceMinor,
                                addressCitySnapshot = city.ifBlank { null },
                                addressStreetSnapshot = street.ifBlank { null },
                                manualNotes = notes.ifBlank { null },
                                status = JobStatus.ACTIVE
                            )
                            jobRepository.createJob(job)
                            onNavigateBack()
                        }
                    }
                },
                enabled = selectedClientId != null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Utwórz zlecenie")
            }
        }
    }
}
