package com.example.firestationops.ui.deficiency

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.firestationops.domain.model.DeficiencySeverity
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeficiencyListScreen(
    viewModel: DeficiencyListViewModel,
    onBack: () -> Unit,
    onDeficiencyClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Open Deficiencies") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("< Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is DeficiencyListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is DeficiencyListUiState.Empty -> {
                    Text(
                        "No open deficiencies found.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                is DeficiencyListUiState.Error -> {
                    Text(
                        state.message,
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is DeficiencyListUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.deficiencies) { item ->
                            DeficiencyItem(item, onDeficiencyClick)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeficiencyItem(
    item: DeficiencyWithApparatus,
    onClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(item.deficiency.id) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.apparatus?.radioName ?: "Unknown Apparatus",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    item.deficiency.title,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    formatDate(item.deficiency.createdAt),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            SeverityBadge(item.deficiency.severity)
        }
    }
}

@Composable
fun SeverityBadge(severity: DeficiencySeverity) {
    val color = when (severity) {
        DeficiencySeverity.OUT_OF_SERVICE -> MaterialTheme.colorScheme.error
        DeficiencySeverity.REPAIR_NEEDED -> Color(0xFFFF9800) // Orange
        DeficiencySeverity.INFORMATIONAL -> Color(0xFF2196F3) // Blue
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Text(
            text = severity.name.replace("_", " "),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

fun formatDate(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dateTime.month.ordinal + 1}/${dateTime.dayOfMonth}/${dateTime.year}"
}
