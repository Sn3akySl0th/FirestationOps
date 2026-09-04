package com.example.firestationops.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.Station
import com.example.firestationops.platform.TextImportResult
import com.example.firestationops.platform.rememberFileImporter
import kotlinx.coroutines.launch
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogSettingsScreen(
    viewModel: CatalogSettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val stationEditor by viewModel.stationEditor.collectAsState()
    val apparatusEditor by viewModel.apparatusEditor.collectAsState()
    val templateEditor by viewModel.templateEditor.collectAsState()
    val templateCsvImport by viewModel.templateCsvImport.collectAsState()
    val historyCsvImport by viewModel.historyCsvImport.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val fileImporter = rememberFileImporter()
    val scope = rememberCoroutineScope()

    LaunchedEffect(actionMessage) {
        actionMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catalog settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val state = uiState) {
            CatalogSettingsUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is CatalogSettingsUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            "Manage stations, apparatus, and inspection templates for your department.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (state.cloudSyncEnabled) {
                            Text(
                                "Changes save locally first, then upload to the cloud when sync runs.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CatalogSection.entries.forEach { section ->
                                FilterChip(
                                    selected = state.section == section,
                                    onClick = { viewModel.selectSection(section) },
                                    label = {
                                        Text(
                                            when (section) {
                                                CatalogSection.STATIONS -> "Stations"
                                                CatalogSection.APPARATUS -> "Apparatus"
                                                CatalogSection.TEMPLATES -> "Templates"
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }

                    when (state.section) {
                        CatalogSection.STATIONS -> {
                            item {
                                SectionHeader(
                                    title = "Stations",
                                    canManage = state.canManageCatalog,
                                    onAdd = viewModel::openNewStationEditor
                                )
                            }
                            if (state.stations.isEmpty()) {
                                item {
                                    Text(
                                        "No stations yet. Add a station to organize apparatus.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else {
                                items(state.stations) { station ->
                                    val apparatusCount = state.apparatus.count { it.stationId == station.id }
                                    StationCard(
                                        station = station,
                                        apparatusCount = apparatusCount,
                                        canManage = state.canManageCatalog,
                                        onClick = { viewModel.openStationEditor(station) }
                                    )
                                }
                            }
                        }
                        CatalogSection.APPARATUS -> {
                            item {
                                SectionHeader(
                                    title = "Apparatus",
                                    canManage = state.canManageCatalog,
                                    onAdd = { viewModel.openNewApparatusEditor(state.stations) }
                                )
                            }
                            if (state.stations.isEmpty()) {
                                item {
                                    Text(
                                        "Add a station before adding apparatus.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else if (state.apparatus.isEmpty()) {
                                item {
                                    Text(
                                        "No apparatus yet. Add apparatus to enable inspections.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else {
                                items(state.apparatus) { apparatus ->
                                    val stationName = state.stations.find { it.id == apparatus.stationId }?.name ?: "Unknown station"
                                    ApparatusCard(
                                        apparatus = apparatus,
                                        stationName = stationName,
                                        canManage = state.canManageCatalog,
                                        onClick = { viewModel.openApparatusEditor(apparatus) }
                                    )
                                }
                            }
                        }
                        CatalogSection.TEMPLATES -> {
                            item {
                                SectionHeader(
                                    title = "Inspection templates",
                                    canManage = state.canManageCatalog,
                                    onAdd = viewModel::openNewTemplateEditor,
                                    secondaryActionLabel = "Import CSV",
                                    onSecondaryAction = { viewModel.openTemplateCsvImport(replaceOpenEditorItems = false) }
                                )
                            }
                            if (state.canManageCatalog) {
                                item {
                                    TextButton(onClick = viewModel::openHistoryCsvImport) {
                                        Text("Import inspection history")
                                    }
                                }
                            }
                            if (state.templates.isEmpty()) {
                                item {
                                    Text(
                                        "No templates yet. Add a template or import a checklist CSV.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            } else {
                                items(state.templates) { template ->
                                    TemplateCard(
                                        template = template,
                                        canManage = state.canManageCatalog,
                                        onClick = { viewModel.openTemplateEditor(template) }
                                    )
                                }
                            }
                        }
                    }

                    if (!state.canManageCatalog) {
                        item {
                            Text(
                                "Only administrators can edit the department catalog.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    stationEditor?.let { editor ->
        StationEditorDialog(
            editor = editor,
            onDismiss = viewModel::closeStationEditor,
            onSave = viewModel::saveStationEditor,
            onNameChange = viewModel::updateStationName,
            onAddressChange = viewModel::updateStationAddress
        )
    }

    apparatusEditor?.let { editor ->
        val successState = uiState as? CatalogSettingsUiState.Success
        val stations = successState?.stations.orEmpty()
        val templatesForType = successState?.templates.orEmpty()
            .filter { it.isActive && it.apparatusType.equals(editor.type, ignoreCase = true) }
        ApparatusEditorDialog(
            editor = editor,
            stations = stations,
            assignableTemplates = templatesForType,
            onDismiss = viewModel::closeApparatusEditor,
            onSave = viewModel::saveApparatusEditor,
            onStationChange = viewModel::updateApparatusStationId,
            onNameChange = viewModel::updateApparatusName,
            onTypeChange = viewModel::updateApparatusType,
            onRadioNameChange = viewModel::updateApparatusRadioName,
            onVinChange = viewModel::updateApparatusVin,
            onLicensePlateChange = viewModel::updateApparatusLicensePlate,
            onBarcodeChange = viewModel::updateApparatusBarcode,
            onStatusChange = viewModel::updateApparatusStatus,
            onToggleAssignedTemplate = viewModel::toggleApparatusAssignedTemplate
        )
    }

    templateEditor?.let { editor ->
        TemplateEditorDialog(
            editor = editor,
            onDismiss = viewModel::closeTemplateEditor,
            onSave = viewModel::saveTemplateEditor,
            onNameChange = viewModel::updateTemplateName,
            onDescriptionChange = viewModel::updateTemplateDescription,
            onApparatusTypeChange = viewModel::updateTemplateApparatusType,
            onFrequencyChange = viewModel::updateTemplateFrequencyHours,
            onActiveChange = viewModel::updateTemplateActive,
            onItemTextChange = viewModel::updateTemplateItemText,
            onAddItem = viewModel::addTemplateItem,
            onRemoveItem = viewModel::removeTemplateItem,
            onImportCsv = { viewModel.openTemplateCsvImport(replaceOpenEditorItems = true) }
        )
    }

    templateCsvImport?.let { importState ->
        TemplateCsvImportDialog(
            state = importState,
            onDismiss = viewModel::closeTemplateCsvImport,
            onCsvTextChange = viewModel::updateTemplateCsvText,
            onPreview = viewModel::previewTemplateCsv,
            onApply = viewModel::applyTemplateCsvImport,
            onPickFile = {
                scope.launch {
                    when (val result = fileImporter.pickTextFile()) {
                        is TextImportResult.Success -> {
                            viewModel.setTemplateCsvFromFile(result.content, result.fileName)
                        }
                        is TextImportResult.Error -> {
                            snackbarHostState.showSnackbar(result.message)
                        }
                        TextImportResult.Cancelled -> Unit
                    }
                }
            }
        )
    }

    historyCsvImport?.let { importState ->
        HistoryCsvImportDialog(
            state = importState,
            onDismiss = viewModel::closeHistoryCsvImport,
            onCsvTextChange = viewModel::updateHistoryCsvText,
            onPreview = viewModel::previewHistoryCsv,
            onApply = viewModel::applyHistoryCsvImport,
            onKeepLocalOnlyChange = viewModel::updateHistoryKeepLocalOnly,
            onPickFile = {
                scope.launch {
                    when (val result = fileImporter.pickTextFile()) {
                        is TextImportResult.Success -> {
                            viewModel.setHistoryCsvFromFile(result.content, result.fileName)
                        }
                        is TextImportResult.Error -> {
                            snackbarHostState.showSnackbar(result.message)
                        }
                        TextImportResult.Cancelled -> Unit
                    }
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    canManage: Boolean,
    onAdd: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (canManage) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    TextButton(onClick = onSecondaryAction) {
                        Text(secondaryActionLabel)
                    }
                }
                TextButton(onClick = onAdd) {
                    Text("Add")
                }
            }
        }
    }
}

@Composable
private fun StationCard(
    station: Station,
    apparatusCount: Int,
    canManage: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canManage) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(station.name, style = MaterialTheme.typography.titleMedium)
            station.address?.let { address ->
                Text(address, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "$apparatusCount apparatus assigned",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun ApparatusCard(
    apparatus: Apparatus,
    stationName: String,
    canManage: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canManage) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(apparatus.radioName, style = MaterialTheme.typography.titleMedium)
            Text("${apparatus.name} • ${apparatus.type}", style = MaterialTheme.typography.bodySmall)
            Text("Station: $stationName", style = MaterialTheme.typography.bodySmall)
            Text(apparatus.status.name, style = MaterialTheme.typography.labelMedium)
            apparatus.barcode?.takeIf { it.isNotBlank() }?.let { barcode ->
                Text("Barcode: $barcode", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: InspectionTemplate,
    canManage: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canManage) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(template.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "${template.apparatusType} • every ${template.frequencyHours}h • v${template.version}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "${template.items.size} checklist items",
                style = MaterialTheme.typography.labelMedium
            )
            if (!template.isActive) {
                Text("Inactive", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StationEditorDialog(
    editor: StationEditorState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.stationId == null) "Add station" else "Edit station") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = onNameChange,
                    label = { Text("Station name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editor.isSaving
                )
                OutlinedTextField(
                    value = editor.address,
                    onValueChange = onAddressChange,
                    label = { Text("Address (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !editor.isSaving
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !editor.isSaving) {
                if (editor.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.isSaving) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ApparatusEditorDialog(
    editor: ApparatusEditorState,
    stations: List<Station>,
    assignableTemplates: List<InspectionTemplate>,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onStationChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    onRadioNameChange: (String) -> Unit,
    onVinChange: (String) -> Unit,
    onLicensePlateChange: (String) -> Unit,
    onBarcodeChange: (String) -> Unit,
    onStatusChange: (ApparatusStatus) -> Unit,
    onToggleAssignedTemplate: (String) -> Unit
) {
    var stationMenuExpanded by remember { mutableStateOf(false) }
    val selectedStation = stations.find { it.id == editor.stationId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.apparatusId == null) "Add apparatus" else "Edit apparatus") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = stationMenuExpanded,
                    onExpandedChange = { stationMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedStation?.name ?: "Select station",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Station") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = stationMenuExpanded) },
                        enabled = !editor.isSaving && stations.isNotEmpty()
                    )
                    ExposedDropdownMenu(
                        expanded = stationMenuExpanded,
                        onDismissRequest = { stationMenuExpanded = false }
                    ) {
                        stations.forEach { station ->
                            DropdownMenuItem(
                                text = { Text(station.name) },
                                onClick = {
                                    onStationChange(station.id)
                                    stationMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = editor.radioName,
                    onValueChange = onRadioNameChange,
                    label = { Text("Radio name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editor.isSaving
                )
                OutlinedTextField(
                    value = editor.barcode,
                    onValueChange = onBarcodeChange,
                    label = { Text("Barcode / QR value (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editor.isSaving
                )
                OutlinedTextField(
                    value = editor.vin,
                    onValueChange = onVinChange,
                    label = { Text("VIN (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editor.isSaving
                )
                OutlinedTextField(
                    value = editor.licensePlate,
                    onValueChange = onLicensePlateChange,
                    label = { Text("License plate (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editor.isSaving
                )
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = onNameChange,
                    label = { Text("Display name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editor.isSaving
                )
                OutlinedTextField(
                    value = editor.type,
                    onValueChange = onTypeChange,
                    label = { Text("Type (Engine, Ladder, etc.)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editor.isSaving
                )
                Text("Status", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApparatusStatus.entries.forEach { status ->
                        FilterChip(
                            selected = editor.status == status,
                            onClick = { onStatusChange(status) },
                            label = { Text(status.name) },
                            enabled = !editor.isSaving
                        )
                    }
                }
                Text("Assigned inspection templates", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Leave empty to use every active template for this type. Select specific checklists (daily + weekly) when both apply.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (assignableTemplates.isEmpty()) {
                    Text(
                        "No active templates for type \"${editor.type.ifBlank { "…" }}\".",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        assignableTemplates.forEach { template ->
                            FilterChip(
                                selected = template.id in editor.assignedTemplateIds,
                                onClick = { onToggleAssignedTemplate(template.id) },
                                label = {
                                    Text("${template.name} (${template.frequencyHours}h)")
                                },
                                enabled = !editor.isSaving
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !editor.isSaving && stations.isNotEmpty()) {
                if (editor.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.isSaving) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TemplateEditorDialog(
    editor: TemplateEditorState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onApparatusTypeChange: (String) -> Unit,
    onFrequencyChange: (String) -> Unit,
    onActiveChange: (Boolean) -> Unit,
    onItemTextChange: (Int, String) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (Int) -> Unit,
    onImportCsv: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.templateId == null) "Add template" else "Edit template") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = onNameChange,
                    label = { Text("Template name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editor.isSaving
                )
                OutlinedTextField(
                    value = editor.description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !editor.isSaving
                )
                OutlinedTextField(
                    value = editor.apparatusType,
                    onValueChange = onApparatusTypeChange,
                    label = { Text("Apparatus type") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editor.isSaving
                )
                OutlinedTextField(
                    value = editor.frequencyHours,
                    onValueChange = onFrequencyChange,
                    label = { Text("Frequency (hours)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editor.isSaving
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Active")
                    Switch(
                        checked = editor.isActive,
                        onCheckedChange = onActiveChange,
                        enabled = !editor.isSaving
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Checklist items (${editor.items.size})", style = MaterialTheme.typography.labelLarge)
                    Row {
                        TextButton(onClick = onImportCsv, enabled = !editor.isSaving) {
                            Text("Import CSV")
                        }
                        TextButton(onClick = onAddItem, enabled = !editor.isSaving) {
                            Text("Add item")
                        }
                    }
                }
                editor.items.forEachIndexed { index, item ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = item.text,
                                onValueChange = { onItemTextChange(index, it) },
                                label = { Text("Item ${index + 1}") },
                                modifier = Modifier.weight(1f),
                                enabled = !editor.isSaving
                            )
                            if (editor.items.size > 1) {
                                TextButton(
                                    onClick = { onRemoveItem(index) },
                                    enabled = !editor.isSaving
                                ) {
                                    Text("Remove")
                                }
                            }
                        }
                        val meta = buildList {
                            item.category?.takeIf { it.isNotBlank() }?.let { add(it) }
                            item.expectedQuantity?.let { add("qty $it") }
                        }.joinToString(" · ")
                        if (meta.isNotEmpty()) {
                            Text(
                                meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !editor.isSaving) {
                if (editor.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.isSaving) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TemplateCsvImportDialog(
    state: TemplateCsvImportState,
    onDismiss: () -> Unit,
    onCsvTextChange: (String) -> Unit,
    onPreview: () -> Unit,
    onApply: () -> Unit,
    onPickFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (state.replaceOpenEditorItems) {
                    "Import checklist CSV"
                } else {
                    "Import template from CSV"
                }
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Columns: category, text, description, expectedQuantity, requiresNoteOnFail",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onPickFile) {
                    Text("Choose file")
                }
                state.fileName?.let { name ->
                    Text("Selected: $name", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedTextField(
                    value = state.csvText,
                    onValueChange = onCsvTextChange,
                    label = { Text("Paste CSV") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    minLines = 8
                )
                state.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                state.preview?.let { preview ->
                    Text(preview.summary, style = MaterialTheme.typography.bodyMedium)
                    preview.warnings.take(5).forEach { warning ->
                        Text(warning, style = MaterialTheme.typography.bodySmall)
                    }
                    if (preview.warnings.size > 5) {
                        Text(
                            "+${preview.warnings.size - 5} more warning(s)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onPreview, enabled = state.csvText.isNotBlank()) {
                    Text("Preview")
                }
                Button(
                    onClick = onApply,
                    enabled = state.csvText.isNotBlank() && state.error == null
                ) {
                    Text(if (state.replaceOpenEditorItems) "Replace items" else "Continue")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun HistoryCsvImportDialog(
    state: HistoryCsvImportState,
    onDismiss: () -> Unit,
    onCsvTextChange: (String) -> Unit,
    onPreview: () -> Unit,
    onApply: () -> Unit,
    onKeepLocalOnlyChange: (Boolean) -> Unit,
    onPickFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.isImporting) onDismiss() },
        title = { Text("Import inspection history") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Long-format CSV: importGroupKey, apparatusRadioName, templateName, completedAt, completedByEmail, category, itemText, status, actualQuantity, note",
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = onPickFile, enabled = !state.isImporting) {
                    Text("Choose file")
                }
                state.fileName?.let { name ->
                    Text("Selected: $name", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedTextField(
                    value = state.csvText,
                    onValueChange = onCsvTextChange,
                    label = { Text("Paste history CSV") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    minLines = 8,
                    enabled = !state.isImporting
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Keep local archive only")
                    Switch(
                        checked = state.keepLocalOnly,
                        onCheckedChange = onKeepLocalOnlyChange,
                        enabled = !state.isImporting
                    )
                }
                state.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                state.preview?.let { preview ->
                    Text(preview.summary, style = MaterialTheme.typography.bodyMedium)
                    preview.inspections.take(5).forEach { inspection ->
                        val label = buildString {
                            append(inspection.importGroupKey)
                            append(": ")
                            if (inspection.canImport) append("ready") else append(inspection.errors.firstOrNull() ?: "blocked")
                        }
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                    if (preview.inspections.size > 5) {
                        Text(
                            "+${preview.inspections.size - 5} more group(s)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onPreview,
                    enabled = state.csvText.isNotBlank() && !state.isImporting
                ) {
                    Text("Preview")
                }
                Button(
                    onClick = onApply,
                    enabled = state.csvText.isNotBlank() &&
                        !state.isImporting &&
                        (state.preview?.importableCount ?: 0) > 0
                ) {
                    if (state.isImporting) {
                        CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    } else {
                        Text("Import ${state.preview?.importableCount ?: 0}")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isImporting) {
                Text("Cancel")
            }
        }
    )
}
