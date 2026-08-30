package com.example.firestationops.ui.incident

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.firestationops.domain.model.AssignmentStatus
import com.example.firestationops.domain.model.CommandLogEntry
import com.example.firestationops.domain.model.CommandLogEntryType
import com.example.firestationops.domain.model.IncidentStatus
import com.example.firestationops.domain.model.IncidentType
import com.example.firestationops.ui.deficiency.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentDetailScreen(
    viewModel: IncidentDetailViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.title.ifBlank { "Incident report" }) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("< Back") }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(androidx.compose.ui.Alignment.Center))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    IncidentStatusBadge(uiState.status)
                    if (uiState.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }

            uiState.error?.let { message ->
                item {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            uiState.infoMessage?.let { message ->
                item {
                    Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::updateTitle,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.canEditFields,
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.summary,
                    onValueChange = viewModel::updateSummary,
                    label = { Text("Summary") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.canEditFields,
                    minLines = 3
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.locationDescription,
                    onValueChange = viewModel::updateLocation,
                    label = { Text("Location description") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.canEditFields,
                    minLines = 2
                )
            }
            item {
                IncidentTypeSelector(
                    selected = uiState.incidentType,
                    enabled = uiState.canEditFields,
                    onSelected = viewModel::updateIncidentType
                )
            }

            if (uiState.status == IncidentStatus.DRAFT) {
                item {
                    Button(
                        onClick = viewModel::activateIncident,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.title.isNotBlank() && !uiState.isSaving
                    ) {
                        Text("Open incident")
                    }
                }
            }
            if (uiState.status != IncidentStatus.CLOSED) {
                item {
                    OutlinedButton(
                        onClick = viewModel::closeIncident,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.title.isNotBlank() && !uiState.isSaving
                    ) {
                        Text("Close incident")
                    }
                }
            }

            item {
                Text("Command board", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Track unit and personnel status at this incident.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            item {
                Text("Units", style = MaterialTheme.typography.titleMedium)
            }

            if (uiState.unitAssignments.isEmpty()) {
                item {
                    Text("No units assigned yet.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(uiState.unitAssignments) { item ->
                    AssignmentBoardCard(
                        label = item.apparatusLabel,
                        status = item.assignment.status,
                        canManage = uiState.canManageAssignments,
                        onStatusSelected = { status ->
                            viewModel.updateUnitStatus(item.assignment.id, status)
                        }
                    )
                }
            }

            if (uiState.canManageAssignments && uiState.availableApparatus.isNotEmpty()) {
                item {
                    Text("Assign unit", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.availableApparatus.forEach { apparatus ->
                            AssistChip(
                                onClick = { viewModel.assignUnit(apparatus.id) },
                                label = { Text(apparatus.radioName) }
                            )
                        }
                    }
                }
            }

            item {
                Text("Personnel", style = MaterialTheme.typography.titleMedium)
            }

            if (uiState.personnelAssignments.isEmpty()) {
                item {
                    Text("No personnel assigned yet.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(uiState.personnelAssignments) { item ->
                    AssignmentBoardCard(
                        label = item.memberLabel,
                        status = item.assignment.status,
                        canManage = uiState.canManageAssignments,
                        onStatusSelected = { status ->
                            viewModel.updatePersonnelStatus(item.assignment.id, status)
                        }
                    )
                }
            }

            if (uiState.canManageAssignments && uiState.availableMembers.isNotEmpty()) {
                item {
                    Text("Assign personnel", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.availableMembers.forEach { member ->
                            AssistChip(
                                onClick = { viewModel.assignPersonnel(member.id) },
                                label = { Text(member.fullName) }
                            )
                        }
                    }
                }
            }

            item {
                Text("Command timeline", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Entries are append-only and attributed to the author.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uiState.timeline.isEmpty()) {
                item {
                    Text("No timeline entries yet.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                items(uiState.timeline) { entry ->
                    CommandLogEntryCard(
                        entry = entry,
                        timeline = uiState.timeline,
                        canCorrect = uiState.canAppendLog && uiState.correctingEntryId == null,
                        onCorrect = viewModel::startCorrection
                    )
                }
            }

            if (uiState.canAppendLog) {
                if (uiState.correctingEntryId != null) {
                    val correctedEntry = uiState.timeline.find { it.id == uiState.correctingEntryId }
                    item {
                        Text("Post correction", style = MaterialTheme.typography.titleMedium)
                        correctedEntry?.let { entry ->
                            Text(
                                "Correcting: ${entry.message}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = uiState.newLogMessage,
                            onValueChange = viewModel::updateNewLogMessage,
                            label = { Text("Correction message") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = viewModel::cancelCorrection,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = viewModel::appendCorrection,
                                modifier = Modifier.weight(1f),
                                enabled = uiState.newLogMessage.isNotBlank()
                            ) {
                                Text("Post correction")
                            }
                        }
                    }
                } else {
                    item {
                        OutlinedTextField(
                            value = uiState.newLogMessage,
                            onValueChange = viewModel::updateNewLogMessage,
                            label = { Text("Add timeline entry") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                    item {
                        Button(
                            onClick = viewModel::appendLogEntry,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = uiState.newLogMessage.isNotBlank()
                        ) {
                            Text("Add entry")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AssignmentBoardCard(
    label: String,
    status: AssignmentStatus,
    canManage: Boolean,
    onStatusSelected: (AssignmentStatus) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                AssignmentStatusBadge(status)
            }
            if (canManage) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    nextStatuses(status).forEach { nextStatus ->
                        FilterChip(
                            selected = false,
                            onClick = { onStatusSelected(nextStatus) },
                            label = { Text(nextStatus.name.replace('_', ' ')) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssignmentStatusBadge(status: AssignmentStatus) {
    val color = when (status) {
        AssignmentStatus.ASSIGNED -> MaterialTheme.colorScheme.secondary
        AssignmentStatus.EN_ROUTE -> MaterialTheme.colorScheme.primary
        AssignmentStatus.ON_SCENE -> MaterialTheme.colorScheme.tertiary
        AssignmentStatus.RELEASED -> MaterialTheme.colorScheme.outline
    }
    Surface(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.small) {
        Text(
            text = status.name.replace('_', ' '),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

private fun nextStatuses(current: AssignmentStatus): List<AssignmentStatus> = when (current) {
    AssignmentStatus.ASSIGNED -> listOf(AssignmentStatus.EN_ROUTE, AssignmentStatus.RELEASED)
    AssignmentStatus.EN_ROUTE -> listOf(AssignmentStatus.ON_SCENE, AssignmentStatus.RELEASED)
    AssignmentStatus.ON_SCENE -> listOf(AssignmentStatus.RELEASED)
    AssignmentStatus.RELEASED -> listOf(AssignmentStatus.ASSIGNED)
}

@Composable
private fun IncidentTypeSelector(
    selected: IncidentType,
    enabled: Boolean,
    onSelected: (IncidentType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Incident type", style = MaterialTheme.typography.labelLarge)
        IncidentType.entries.chunked(3).forEach { rowTypes ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTypes.forEach { type ->
                    FilterChip(
                        selected = selected == type,
                        onClick = { if (enabled) onSelected(type) },
                        label = { Text(type.name.replace('_', ' ')) },
                        enabled = enabled
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandLogEntryCard(
    entry: CommandLogEntry,
    timeline: List<CommandLogEntry>,
    canCorrect: Boolean,
    onCorrect: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (entry.entryType == CommandLogEntryType.CORRECTION) {
                Text("Correction", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                entry.correctsEntryId?.let { correctedId ->
                    timeline.find { it.id == correctedId }?.let { corrected ->
                        Text(
                            "Re: ${corrected.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
            Text(entry.message, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            val timeLabel = entry.incidentTimestamp?.let { "Scene ${formatDate(it)} · " }.orEmpty()
            Text(
                "${timeLabel}Logged ${formatDate(entry.createdAt)}",
                style = MaterialTheme.typography.bodySmall
            )
            if (canCorrect && entry.entryType == CommandLogEntryType.LOG) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = { onCorrect(entry.id) }) {
                    Text("Correct entry")
                }
            }
        }
    }
}
