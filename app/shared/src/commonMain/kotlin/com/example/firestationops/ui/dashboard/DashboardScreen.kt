package com.example.firestationops.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusInspectionStatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.InspectionComplianceStatus
import com.example.firestationops.domain.model.Station
import com.example.firestationops.domain.sync.SyncRunnerState
import com.example.firestationops.ui.deficiency.DeficiencyItem
import com.example.firestationops.ui.deficiency.DeficiencyWithApparatus

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onApparatusClick: (String) -> Unit,
    onOpenDeficienciesClick: () -> Unit,
    onDeficiencyClick: (String) -> Unit = {},
    onOpenIncidentsClick: () -> Unit = {},
    showDepartmentSettings: Boolean = false,
    onOpenDepartmentSettings: () -> Unit = {},
    onSyncNowClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val cloudSyncEnabled = viewModel.cloudSyncEnabled

    when (val state = uiState) {
        DashboardUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is DashboardUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is DashboardUiState.Success -> {
            DashboardContent(
                state = state,
                syncState = syncState,
                syncMessage = syncMessage,
                cloudSyncEnabled = cloudSyncEnabled,
                onApparatusClick = onApparatusClick,
                onOpenDeficienciesClick = onOpenDeficienciesClick,
                onDeficiencyClick = onDeficiencyClick,
                onOpenIncidentsClick = onOpenIncidentsClick,
                showDepartmentSettings = showDepartmentSettings,
                onOpenDepartmentSettings = onOpenDepartmentSettings,
                onSyncNowClick = {
                    viewModel.syncNow()
                    onSyncNowClick()
                },
                onDismissSyncMessage = viewModel::clearSyncMessage
            )
        }
    }
}

@Composable
private fun DashboardContent(
    state: DashboardUiState.Success,
    syncState: SyncRunnerState,
    syncMessage: String?,
    cloudSyncEnabled: Boolean,
    onApparatusClick: (String) -> Unit,
    onOpenDeficienciesClick: () -> Unit,
    onDeficiencyClick: (String) -> Unit,
    onOpenIncidentsClick: () -> Unit,
    showDepartmentSettings: Boolean,
    onOpenDepartmentSettings: () -> Unit,
    onSyncNowClick: () -> Unit,
    onDismissSyncMessage: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(syncMessage) {
        syncMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onDismissSyncMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        val isWide = maxWidth >= 900.dp

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    dashboardMainItems(
                        state,
                        syncState,
                        cloudSyncEnabled,
                        onApparatusClick,
                        onOpenDeficienciesClick,
                        onDeficiencyClick,
                        onOpenIncidentsClick,
                        showDepartmentSettings,
                        onOpenDepartmentSettings,
                        onSyncNowClick
                    )
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { StationsSection(state.stations, onApparatusClick) }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                dashboardMainItems(
                    state,
                    syncState,
                    cloudSyncEnabled,
                    onApparatusClick,
                    onOpenDeficienciesClick,
                    onDeficiencyClick,
                    onOpenIncidentsClick,
                    showDepartmentSettings,
                    onOpenDepartmentSettings,
                    onSyncNowClick
                )
                item { StationsSection(state.stations, onApparatusClick) }
            }
        }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dashboardMainItems(
    state: DashboardUiState.Success,
    syncState: SyncRunnerState,
    cloudSyncEnabled: Boolean,
    onApparatusClick: (String) -> Unit,
    onOpenDeficienciesClick: () -> Unit,
    onDeficiencyClick: (String) -> Unit,
    onOpenIncidentsClick: () -> Unit,
    showDepartmentSettings: Boolean,
    onOpenDepartmentSettings: () -> Unit,
    onSyncNowClick: () -> Unit
) {
    item {
        Text("Officer Dashboard", style = MaterialTheme.typography.headlineMedium)
    }
    if (showDepartmentSettings) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenDepartmentSettings() }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Department settings", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "View members and bootstrap the cloud catalog for new departments.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
    if (cloudSyncEnabled || state.summary.pendingSyncCount > 0 || syncState == SyncRunnerState.RUNNING) {
        item {
            SyncStatusCard(
                pendingCount = state.summary.pendingSyncCount,
                syncState = syncState,
                cloudSyncEnabled = cloudSyncEnabled,
                onSyncNowClick = onSyncNowClick
            )
        }
    }
    item {
        SummaryRow(state.summary)
    }
    item {
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onOpenIncidentsClick() }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Incident reports", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Create drafts, open incidents, and maintain an append-only command timeline.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    if (state.overdueInspections.isNotEmpty()) {
        item {
            SectionTitle("Overdue Inspections")
        }
        items(state.overdueInspections) { item ->
            OverdueInspectionCard(item, onApparatusClick)
        }
    }
    if (state.summary.openDeficiencyCount > 0) {
        item {
            DeficiencySummaryCard(
                summary = state.deficiencySummary,
                onClick = onOpenDeficienciesClick
            )
        }
        if (state.topDeficiencies.isNotEmpty()) {
            item {
                SectionTitle("Priority Deficiencies")
            }
            items(state.topDeficiencies) { item ->
                DeficiencyItem(item, onDeficiencyClick)
            }
            item {
                TextButton(onClick = onOpenDeficienciesClick) {
                    Text("View all deficiencies")
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun SummaryRow(summary: DashboardSummary) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SummaryCard(
                label = "Overdue",
                value = summary.overdueCount.toString(),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        item {
            SummaryCard(
                label = "Due Soon",
                value = summary.dueSoonCount.toString(),
                containerColor = Color(0xFFFFF3E0),
                contentColor = Color(0xFFE65100)
            )
        }
        item {
            SummaryCard(
                label = "Open Deficiencies",
                value = summary.openDeficiencyCount.toString(),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        item {
            SummaryCard(
                label = "Out of Service",
                value = summary.outOfServiceApparatusCount.toString(),
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }
        if (summary.pendingSyncCount > 0) {
            item {
                SummaryCard(
                    label = "Pending Sync",
                    value = summary.pendingSyncCount.toString(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SyncStatusCard(
    pendingCount: Int,
    syncState: SyncRunnerState,
    cloudSyncEnabled: Boolean,
    onSyncNowClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Cloud sync", style = MaterialTheme.typography.titleMedium)
                Text(
                    when (syncState) {
                        SyncRunnerState.RUNNING -> "Sync in progress..."
                        SyncRunnerState.FAILED -> "Last sync had errors. Try again when online."
                        SyncRunnerState.IDLE -> when {
                            pendingCount > 0 -> "$pendingCount record(s) waiting to upload."
                            cloudSyncEnabled -> "Download cloud records or upload local changes."
                            else -> "No pending changes."
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (syncState == SyncRunnerState.RUNNING) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Button(onClick = onSyncNowClick) {
                    Text("Sync now")
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    containerColor: Color,
    contentColor: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.width(140.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = contentColor)
            Text(label, style = MaterialTheme.typography.bodySmall, color = contentColor)
        }
    }
}

@Composable
private fun OverdueInspectionCard(
    item: OverdueInspectionItem,
    onApparatusClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onApparatusClick(item.apparatus.id) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.apparatus.radioName, style = MaterialTheme.typography.titleMedium)
                Text(
                    item.compliance.templateName ?: "Inspection required",
                    style = MaterialTheme.typography.bodySmall
                )
                val subtitle = when (item.compliance.status) {
                    InspectionComplianceStatus.NEVER_INSPECTED -> "Never inspected"
                    InspectionComplianceStatus.OVERDUE -> {
                        if (item.compliance.daysOverdue > 0) {
                            "${item.compliance.daysOverdue} day(s) overdue"
                        } else {
                            "Overdue"
                        }
                    }
                    else -> null
                }
                subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            ComplianceBadge(item.compliance)
        }
    }
}

@Composable
private fun DeficiencySummaryCard(
    summary: com.example.firestationops.domain.model.DeficiencySummary,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${summary.total} Open Deficiencies",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "OOS: ${summary.outOfService} · Repair: ${summary.repairNeeded} · Info: ${summary.informational}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                "Tap to view and resolve",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun StationsSection(
    stations: List<StationDashboardSection>,
    onApparatusClick: (String) -> Unit
) {
    Text("Stations", style = MaterialTheme.typography.titleLarge)
    Spacer(modifier = Modifier.height(8.dp))
    stations.forEach { section ->
        StationCard(
            station = section.station,
            apparatusList = section.apparatus,
            onApparatusClick = onApparatusClick
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun StationCard(
    station: Station,
    apparatusList: List<ApparatusDashboardItem>,
    onApparatusClick: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(station.name, style = MaterialTheme.typography.titleLarge)
            station.address?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            if (apparatusList.isEmpty()) {
                Text("No apparatus assigned", style = MaterialTheme.typography.bodyMedium)
            } else {
                apparatusList.forEach { item ->
                    ApparatusItem(item, onApparatusClick)
                }
            }
        }
    }
}

@Composable
fun ApparatusItem(
    item: ApparatusDashboardItem,
    onApparatusClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onApparatusClick(item.apparatus.id) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.apparatus.radioName, style = MaterialTheme.typography.titleMedium)
            Text(item.apparatus.type, style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item.compliance?.let { ComplianceBadge(it) }
            StatusBadge(item.apparatus.status)
        }
    }
}

@Composable
fun ComplianceBadge(compliance: ApparatusInspectionStatus) {
    val (label, color) = when (compliance.status) {
        InspectionComplianceStatus.CURRENT -> "Inspected" to Color(0xFF4CAF50)
        InspectionComplianceStatus.DUE_SOON -> "Due soon" to Color(0xFFFF9800)
        InspectionComplianceStatus.OVERDUE -> "Overdue" to MaterialTheme.colorScheme.error
        InspectionComplianceStatus.NEVER_INSPECTED -> "Overdue" to MaterialTheme.colorScheme.error
        InspectionComplianceStatus.IN_PROGRESS -> "In progress" to Color(0xFF2196F3)
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun StatusBadge(status: ApparatusStatus) {
    val color = when (status) {
        ApparatusStatus.IN_SERVICE -> Color(0xFF4CAF50)
        ApparatusStatus.OUT_OF_SERVICE -> MaterialTheme.colorScheme.error
        ApparatusStatus.MAINTENANCE -> Color(0xFFFF9800)
        ApparatusStatus.RESERVE -> Color(0xFF2196F3)
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(1.dp, color)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (status == ApparatusStatus.OUT_OF_SERVICE) "⚠ " else "",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
            Text(
                text = status.name.replace("_", " "),
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}
