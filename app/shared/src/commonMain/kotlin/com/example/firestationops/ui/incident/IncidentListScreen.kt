package com.example.firestationops.ui.incident

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.model.IncidentStatus
import com.example.firestationops.ui.deficiency.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentListScreen(
    viewModel: IncidentListViewModel,
    onBack: () -> Unit,
    onIncidentClick: (String) -> Unit,
    onCreateIncident: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Incidents") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("< Back") }
                },
                actions = {
                    TextButton(onClick = onCreateIncident) { Text("New") }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                IncidentListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                IncidentListUiState.Empty -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No incidents yet.", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onCreateIncident) { Text("Create incident report") }
                    }
                }
                is IncidentListUiState.Error -> {
                    Text(
                        state.message,
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is IncidentListUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.incidents) { incident ->
                            IncidentListItem(incident, onIncidentClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncidentListItem(incident: Incident, onClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(incident.id) }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = incident.title.ifBlank { "Untitled draft" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IncidentStatusBadge(incident.status)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(incident.incidentType.name.replace('_', ' '), style = MaterialTheme.typography.bodySmall)
            Text("Updated ${formatDate(incident.updatedAt)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun IncidentStatusBadge(status: IncidentStatus) {
    val (label, color) = when (status) {
        IncidentStatus.DRAFT -> "Draft" to MaterialTheme.colorScheme.secondary
        IncidentStatus.ACTIVE -> "Active" to MaterialTheme.colorScheme.primary
        IncidentStatus.CLOSED -> "Closed" to MaterialTheme.colorScheme.outline
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}
