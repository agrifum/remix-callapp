package com.example.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ClientEntity
import com.example.data.entity.NoteEntity
import com.example.data.entity.ServiceEntity
import com.example.ui.theme.StatusGreen

@Composable
fun CallOverlayContent(
    phoneKey: String,
    phoneDisplay: String,
    existingClient: ClientEntity?,
    pastNotes: List<NoteEntity>,
    activeServices: List<ServiceEntity>,
    onSaveAndClose: (
        noteText: String,
        markAsClient: Boolean,
        clientName: String?,
        createJob: Boolean,
        serviceId: String?,
        toTasks: Boolean
    ) -> Unit,
    onDismiss: () -> Unit,
    onDraftChange: (String) -> Unit
) {
    var noteText by remember { mutableStateOf("") }
    var markAsClient by remember { mutableStateOf(existingClient != null) }
    var clientNameInput by remember { mutableStateOf(existingClient?.displayName ?: "") }
    var createJob by remember { mutableStateOf(false) }
    var selectedServiceId by remember { mutableStateOf<String?>(null) }
    var isPastNotesExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(noteText) {
        onDraftChange(noteText)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Call status, number, close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(StatusGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = existingClient?.displayName ?: phoneDisplay,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (existingClient != null) {
                            Text(
                                text = phoneDisplay,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Zamknij nakładkę")
                }
            }

            // Past notes accordion if client has previous notes
            if (pastNotes.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isPastNotesExpanded = !isPastNotesExpanded }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = if (isPastNotesExpanded) "Ukryj poprzednie notatki (${pastNotes.size})"
                            else "Poprzednia notatka: \"${pastNotes.first().rawText}\"",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = if (isPastNotesExpanded) 10 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Quick Note Input
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = { Text("Wpisz notatkę z rozmowy...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )

            // Client flag & name edit if not a client yet
            if (existingClient == null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = markAsClient,
                        onCheckedChange = { markAsClient = it }
                    )
                    Text(
                        text = "Oznacz jako Klienta",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (markAsClient) {
                    OutlinedTextField(
                        value = clientNameInput,
                        onValueChange = { clientNameInput = it },
                        placeholder = { Text("Nazwisko i imię klienta") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Create job checkbox and service chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = createJob,
                    onCheckedChange = { createJob = it }
                )
                Text(
                    text = "Utwórz Zlecenie",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (createJob && activeServices.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeServices.take(3).forEach { s ->
                        FilterChip(
                            selected = selectedServiceId == s.id,
                            onClick = {
                                selectedServiceId = if (selectedServiceId == s.id) null else s.id
                            },
                            label = { Text(s.name, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // Action Buttons: Do Zadań & Zapisz
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onSaveAndClose(
                            noteText,
                            markAsClient,
                            clientNameInput.ifBlank { null },
                            createJob,
                            selectedServiceId,
                            true
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Do Zadań", fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        onSaveAndClose(
                            noteText,
                            markAsClient,
                            clientNameInput.ifBlank { null },
                            createJob,
                            selectedServiceId,
                            false
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Zapisz", fontSize = 13.sp)
                }
            }
        }
    }
}
