package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.ReengagementSource
import com.example.data.entity.ClientEntity
import com.example.data.entity.ReengagementEventEntity

@Composable
fun ReengagementDialog(
    event: ReengagementEventEntity,
    client: ClientEntity?,
    onResumeJob: () -> Unit,
    onNewJob: () -> Unit,
    onIgnore: () -> Unit
) {
    val sourceText = if (event.source == ReengagementSource.INCOMING_CALL) "połączenie telefoniczne" else "wiadomość SMS"
    val clientName = client?.displayName ?: "Klient"

    AlertDialog(
        onDismissRequest = onIgnore,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Powracający klient")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "$clientName skontaktował(a) się przez $sourceText.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Ten klient ma już w historii zakończone zlecenia. Czy chcesz wznowić poprzednie zlecenie czy utworzyć nowe?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onNewJob) {
                Text("Nowe zlecenie")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onIgnore) {
                    Text("Pomiń")
                }
                OutlinedButton(onClick = onResumeJob) {
                    Text("Wznów poprzednie")
                }
            }
        }
    )
}
