package com.example.firestationops.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.firestationops.domain.sync.SyncConflictResolution

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncConflictScreen(
    viewModel: SyncConflictViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync conflicts") },
                navigationIcon = {
                    OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "These records were changed on this device and in the cloud. Choose which version to keep.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            uiState.message?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.primary)
            }
            uiState.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            if (uiState.conflicts.isEmpty() && !uiState.isLoading) {
                Text("No sync conflicts right now.")
            }

            uiState.conflicts.forEach { item ->
                SyncConflictCard(
                    item = item,
                    onKeepLocal = { viewModel.resolve(item.conflict, SyncConflictResolution.KEEP_LOCAL) },
                    onKeepRemote = { viewModel.resolve(item.conflict, SyncConflictResolution.KEEP_REMOTE) }
                )
            }
        }
    }
}

@Composable
private fun SyncConflictCard(
    item: SyncConflictItemUi,
    onKeepLocal: () -> Unit,
    onKeepRemote: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text(
                item.conflict.recordType.name.lowercase().replaceFirstChar { it.titlecase() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ConflictVersionColumn(label = "This device", summary = item.localSummary)
            ConflictVersionColumn(label = "Cloud", summary = item.remoteSummary)

            HorizontalDivider()

            if (item.isResolving) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onKeepRemote,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Keep cloud")
                    }
                    Button(
                        onClick = onKeepLocal,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Keep this device")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictVersionColumn(label: String, summary: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(summary, style = MaterialTheme.typography.bodyMedium)
    }
}
