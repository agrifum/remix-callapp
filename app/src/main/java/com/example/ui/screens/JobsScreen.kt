package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.entity.ClientEntity
import com.example.data.entity.JobEntity
import com.example.data.repository.ClientRepository
import com.example.data.repository.JobRepository
import com.example.ui.theme.StatusGray
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusOrange
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JobsScreen(
    jobRepository: JobRepository,
    clientRepository: ClientRepository,
    onJobClick: (String) -> Unit,
    onNewJobClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Aktywne", "Zakończone", "Zamknięte", "Archiwalne")

    val activeJobs by jobRepository.getJobsByStatus(JobStatus.ACTIVE).collectAsState(initial = emptyList())
    val completedJobs by jobRepository.getJobsByStatus(JobStatus.COMPLETED).collectAsState(initial = emptyList())
    val closedJobs by jobRepository.getJobsByStatus(JobStatus.CLOSED).collectAsState(initial = emptyList())
    val archivedJobs by jobRepository.getArchivedJobs().collectAsState(initial = emptyList())

    val currentJobs = when (selectedTab) {
        0 -> activeJobs
        1 -> completedJobs
        2 -> closedJobs
        else -> archivedJobs
    }

    val clients by clientRepository.allClients.collectAsState(initial = emptyList())
    val clientMap = remember(clients) { clients.associateBy { it.id } }

    var selectedJobIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedJobIds.isNotEmpty()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Selection Bar or Tabs
            if (isSelectionMode) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Wybrano: ${selectedJobIds.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Compute safe actions intersection (§14)
                            val selectedJobsList = currentJobs.filter { it.id in selectedJobIds }
                            val allActive = selectedJobsList.isNotEmpty() && selectedJobsList.all { it.status == JobStatus.ACTIVE }
                            val allCompleted = selectedJobsList.isNotEmpty() && selectedJobsList.all { it.status == JobStatus.COMPLETED }

                            if (allActive) {
                                IconButton(onClick = {
                                    scope.launch {
                                        selectedJobIds.forEach { id -> jobRepository.completeJob(id) }
                                        selectedJobIds = emptySet()
                                    }
                                }) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Zakończ zaznaczone", tint = StatusGreen)
                                }
                            }

                            if (allCompleted) {
                                IconButton(onClick = {
                                    scope.launch {
                                        selectedJobIds.forEach { id -> jobRepository.closeJob(id) }
                                        selectedJobIds = emptySet()
                                    }
                                }) {
                                    Icon(Icons.Default.Check, contentDescription = "Zamknij zaznaczone")
                                }
                            }

                            // Shared safe actions: Archiwizuj and Usuń
                            IconButton(onClick = {
                                scope.launch {
                                    selectedJobIds.forEach { id -> jobRepository.setJobArchived(id, true) }
                                    selectedJobIds = emptySet()
                                }
                            }) {
                                Icon(Icons.Default.Archive, contentDescription = "Archiwizuj zaznaczone")
                            }

                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Usuń zaznaczone", tint = MaterialTheme.colorScheme.error)
                            }

                            IconButton(onClick = { selectedJobIds = emptySet() }) {
                                Icon(Icons.Default.Close, contentDescription = "Anuluj wybór")
                            }
                        }
                    }
                }
            } else {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 16.dp
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                selectedJobIds = emptySet()
                            },
                            text = { Text(title) }
                        )
                    }
                }
            }

            if (currentJobs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Brak zleceń w tej kategorii",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Utwórz nowe zlecenie za pomocą przycisku '+' lub z poziomu symulatora połączeń.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(currentJobs, key = { it.id }) { job ->
                        val client = clientMap[job.clientId]
                        val isSelected = job.id in selectedJobIds

                        val statusColor = when (job.status) {
                            JobStatus.ACTIVE -> StatusGreen
                            JobStatus.COMPLETED -> StatusOrange
                            JobStatus.CLOSED -> StatusGray
                        }
                        val statusText = when (job.status) {
                            JobStatus.ACTIVE -> "Aktywne"
                            JobStatus.COMPLETED -> "Zakończone"
                            JobStatus.CLOSED -> "Zamknięte"
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedJobIds = if (isSelected) selectedJobIds - job.id else selectedJobIds + job.id
                                        } else {
                                            onJobClick(job.id)
                                        }
                                    },
                                    onLongClick = {
                                        selectedJobIds = if (isSelected) selectedJobIds - job.id else selectedJobIds + job.id
                                    }
                                ),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        if (isSelectionMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { checked ->
                                                    selectedJobIds = if (checked) selectedJobIds + job.id else selectedJobIds - job.id
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = job.serviceNameSnapshot ?: "Zlecenie",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = statusColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = statusText,
                                            color = statusColor,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                val clientDisplayName = client?.displayName ?: client?.phoneDisplay
                                if (!clientDisplayName.isNullOrBlank()) {
                                    Text(
                                        text = "Klient: $clientDisplayName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val addressParts = listOfNotNull(
                                    job.addressCitySnapshot ?: client?.city,
                                    job.addressStreetSnapshot ?: client?.street,
                                    job.addressBuildingSnapshot ?: client?.buildingNumber
                                ).filter { it.isNotBlank() }

                                if (addressParts.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = addressParts.joinToString(" "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                val term = DateTimeFormatters.formatJobTerm(
                                    job.preliminaryDateEpochDay,
                                    job.preliminaryTimeMinute,
                                    job.preliminaryTimeQualifier,
                                    job.confirmedStartAt
                                )
                                if (term.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = term,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (job.priceMinor != null && job.priceMinor > 0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Cena: ${DateTimeFormatters.formatMoney(job.priceMinor)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }

                                // Quick Actions Row (§13): Zadzwoń, SMS, Nawiguj, Zakończ
                                if (!isSelectionMode) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val phone = client?.phoneKey ?: client?.phoneDisplay
                                        if (!phone.isNullOrBlank()) {
                                            IconButton(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                                        data = Uri.parse("tel:$phone")
                                                    }
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Default.Call, contentDescription = "Zadzwoń", tint = MaterialTheme.colorScheme.primary)
                                            }

                                            IconButton(
                                                onClick = {
                                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                        data = Uri.parse("smsto:$phone")
                                                    }
                                                    context.startActivity(intent)
                                                },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "SMS", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }

                                        val fullAddr = addressParts.joinToString(" ").trim()
                                        if (fullAddr.isNotBlank()) {
                                            IconButton(
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
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(Icons.Default.Navigation, contentDescription = "Nawiguj", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }

                                        if (job.status == JobStatus.ACTIVE) {
                                            Spacer(modifier = Modifier.weight(1f))
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch { jobRepository.completeJob(job.id) }
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Text("Zakończ", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isSelectionMode) {
            FloatingActionButton(
                onClick = onNewJobClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nowe zlecenie")
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Potwierdzenie usunięcia") },
            text = { Text("Czy na pewno chcesz przenieść wybrane zlecenia (${selectedJobIds.size}) do Kosza? Będziesz mógł je przywrócić przez 30 dni.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            selectedJobIds.forEach { id -> jobRepository.softDeleteJob(id) }
                            selectedJobIds = emptySet()
                            showDeleteConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Usuń")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}