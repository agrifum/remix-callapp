package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.preferences.AppPreferences
import com.example.system.build.BuildIdentity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appPreferences: AppPreferences,
    onNavigateBack: () -> Unit,
    onNavigateToServices: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateToSmsTemplates: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val smsAnalysisEnabled by appPreferences.smsAnalysisGlobalEnabled.collectAsState(initial = true)
    val showClientTags by appPreferences.showClientTags.collectAsState(initial = true)
    val mapsEtaEnabled by appPreferences.mapsEtaParsingEnabled.collectAsState(initial = true)
    val buildIdentity = remember { BuildIdentity(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.BUILD_COMMIT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ustawienia") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Wstecz") } }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column {
                    SettingsLinkRow(Icons.Default.Security, "Uprawnienia", "Sprawdź dostęp telefonu, historii, kontaktów i nakładki", onNavigateToPermissions)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsLinkRow(Icons.Default.Build, "Katalog usług i cennik", "Zarządzaj usługami i domyślnymi stawkami PLN", onNavigateToServices)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsLinkRow(Icons.Default.Message, "Szablony SMS", "Zarządzaj szybkimi odpowiedziami i zmiennymi wiadomości", onNavigateToSmsTemplates)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsLinkRow(Icons.Default.Delete, "Kosz (usunięte elementy)", "Przywracaj usunięte zlecenia, notatki i zadania (30 dni)", onNavigateToTrash, error = true)
                }
            }

            Text("Preferencje automatyzacji", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    PreferenceRow(
                        "Analiza SMS w otwartych oknach zlecenia",
                        "Automatycznie proponuje adresy i terminy z wiadomości SMS od klientów z aktywnym zleceniem",
                        smsAnalysisEnabled
                    ) { checked -> scope.launch { appPreferences.setSmsAnalysisGlobalEnabled(checked) } }
                    HorizontalDivider()
                    PreferenceRow(
                        "Wyświetlaj tagi pochodne klientów",
                        "Pokazuj tagi miejscowości, dzielnic i aktywnych usług na kartach klientów",
                        showClientTags
                    ) { checked -> scope.launch { appPreferences.setShowClientTags(checked) } }
                    HorizontalDivider()
                    PreferenceRow(
                        "Odczyt ETA z powiadomień Google Maps",
                        "Pozwala odczytać przewidywany czas dojazdu bez sprawdzania GPS w tle",
                        mapsEtaEnabled
                    ) { checked -> scope.launch { appPreferences.setMapsEtaParsingEnabled(checked) } }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Wersja aplikacji", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(buildIdentity.displayLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Gwarancja prywatności (Local-first)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "CallUpp działa wyłącznie lokalnie na Twoim urządzeniu. Twoje kontakty, rozmowy, notatki i zlecenia nie są wysyłane do żadnej chmury ani baz CRM stron trzecich.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsLinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit, error: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PreferenceRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
