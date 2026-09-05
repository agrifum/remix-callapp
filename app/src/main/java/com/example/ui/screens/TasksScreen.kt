package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.core.model.TaskStatus
import com.example.core.phone.PhoneNumberNormalizer
import com.example.core.time.DateTimeFormatters
import com.example.data.entity.NoteEntity
import com.example.data.repository.NoteRepository
import com.example.data.repository.TaskRepository
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun TasksScreen(
    taskRepository: TaskRepository,
    noteRepository: NoteRepository,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val tasksWithNotes by taskRepository.allActiveTasks.collectAsState(initial = emptyList())

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var newTaskText by remember { mutableStateOf("") }
    var newTaskPhone by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        if (tasksWithNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Wszystkie zadania wykonane!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Zadania tworzą się automatycznie podczas zaznaczenia opcji w nakładce połączenia lub możesz dodać je tutaj.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showAddTaskDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Dodaj zadanie")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tasksWithNotes, key = { it.taskId }) { item ->
                    val isDone = item.status == TaskStatus.DONE
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDone) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val newStatus = if (isDone) TaskStatus.OPEN else TaskStatus.DONE
                                        taskRepository.setTaskStatus(item.taskId, newStatus)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                                    contentDescription = if (isDone) "Oznacz jako otwarte" else "Oznacz jako gotowe",
                                    tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.noteText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isDone) FontWeight.Normal else FontWeight.Medium,
                                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Numer: ${item.phoneKey} • ${DateTimeFormatters.formatDateTime(item.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    scope.launch {
                                        taskRepository.softDeleteTask(item.taskId)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Usuń zadanie",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddTaskDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Dodaj zadanie")
        }
    }

    if (showAddTaskDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddTaskDialog = false
                newTaskText = ""
                newTaskPhone = ""
            },
            title = { Text("Nowe Zadanie") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTaskPhone,
                        onValueChange = { newTaskPhone = it },
                        label = { Text("Numer telefonu") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTaskText,
                        onValueChange = { newTaskText = it },
                        label = { Text("Treść zadania (np. Oddzwonić z wyceną)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val normalizedPhone = PhoneNumberNormalizer.normalizeKey(newTaskPhone)
                        if (newTaskText.isNotBlank() && normalizedPhone.isNotBlank()) {
                            scope.launch {
                                val noteId = UUID.randomUUID().toString()
                                val note = NoteEntity(
                                    id = noteId,
                                    phoneKey = normalizedPhone,
                                    rawText = newTaskText.trim()
                                )
                                noteRepository.insertNote(note)
                                taskRepository.createTask(noteId)
                                showAddTaskDialog = false
                                newTaskText = ""
                                newTaskPhone = ""
                            }
                        }
                    }
                ) {
                    Text("Dodaj")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddTaskDialog = false
                    newTaskText = ""
                    newTaskPhone = ""
                }) {
                    Text("Anuluj")
                }
            }
        )
    }
}
