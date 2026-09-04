package com.example.firestationops.ui.inspection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.firestationops.domain.InspectionChecklistSections
import com.example.firestationops.domain.InspectionValidationRules
import com.example.firestationops.domain.model.Attachment
import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.InspectionResponse
import com.example.firestationops.domain.model.InspectionStatus
import com.example.firestationops.domain.model.InspectionTemplateItem
import com.example.firestationops.domain.model.SyncStatus
import com.example.firestationops.domain.sync.AttachmentUploadProgressTracker
import com.example.firestationops.domain.sync.PendingSyncQueueBuilder
import com.example.firestationops.platform.ExportResult
import com.example.firestationops.platform.rememberFileExporter
import com.example.firestationops.platform.rememberMediaPicker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionScreen(
    viewModel: InspectionViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val attachmentsById by viewModel.attachmentsById.collectAsState()
    val uploadProgress by AttachmentUploadProgressTracker.activeUploads.collectAsState()
    var currentPickingItemId by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val fileExporter = rememberFileExporter()
    val collapsedSections = remember { mutableStateMapOf<String, Boolean>() }

    val mediaPicker = rememberMediaPicker { path ->
        path?.let {
            currentPickingItemId?.let { itemId ->
                viewModel.addAttachment(itemId, it)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.apparatus?.radioName ?: "Inspection") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("< Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.isSuccess) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("✓", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Inspection Submitted", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    exportMessage = when (val result = viewModel.exportCsv(fileExporter)) {
                                        ExportResult.Success -> "CSV saved"
                                        ExportResult.Cancelled -> "CSV export cancelled"
                                        is ExportResult.Error -> result.message
                                    }
                                }
                            }
                        ) {
                            Text("Export CSV")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    exportMessage = when (val result = viewModel.exportPdf(fileExporter)) {
                                        ExportResult.Success -> "PDF saved"
                                        ExportResult.Cancelled -> "PDF export cancelled"
                                        is ExportResult.Error -> result.message
                                    }
                                }
                            }
                        ) {
                            Text("Export PDF")
                        }
                    }
                    exportMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(message, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text("Return to Dashboard")
                    }
                }
            } else if (uiState.needsTemplateSelection) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Choose inspection for ${uiState.apparatus?.radioName ?: "apparatus"}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "This apparatus has more than one assigned checklist.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    uiState.availableTemplates.forEach { template ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.selectTemplate(template.id) }
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(template.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Every ${template.frequencyHours}h · ${template.items.size} items",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            } else {
                val template = uiState.template
                if (template != null) {
                    val progress = remember(template.items, uiState.responses) {
                        InspectionChecklistSections.build(template.items, uiState.responses)
                    }
                    // Collapse large checklists by default after the first section
                    LaunchedEffect(progress.sections.map { it.category }) {
                        if (collapsedSections.isEmpty() && progress.totalCount > 30) {
                            progress.sections.drop(1).forEach { section ->
                                collapsedSections[section.category] = true
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        if (uiState.error != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = uiState.error!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(template.name, style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(progress.progressLabel, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progress.fraction },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            item {
                                VehicleStatusCard(
                                    odometerMiles = uiState.odometerMiles,
                                    fluidOil = uiState.fluidOil,
                                    fluidTransmission = uiState.fluidTransmission,
                                    fluidFuel = uiState.fluidFuel,
                                    fluidAntifreeze = uiState.fluidAntifreeze,
                                    fluidPowerSteering = uiState.fluidPowerSteering,
                                    onOdometerChange = { miles ->
                                        viewModel.updateVehicleStatus(odometerMiles = miles)
                                    },
                                    onFluidChange = { oil, tran, fuel, anti, ps ->
                                        viewModel.updateVehicleStatus(
                                            fluidOil = oil,
                                            fluidTransmission = tran,
                                            fluidFuel = fuel,
                                            fluidAntifreeze = anti,
                                            fluidPowerSteering = ps
                                        )
                                    }
                                )
                            }

                            progress.sections.forEach { section ->
                                val collapsed = collapsedSections[section.category] == true
                                item(key = "header-${section.category}") {
                                    SectionHeaderCard(
                                        section = section,
                                        collapsed = collapsed,
                                        onToggle = {
                                            collapsedSections[section.category] = !collapsed
                                        },
                                        onMarkAllPresent = {
                                            viewModel.markSectionPresent(section.category)
                                        }
                                    )
                                }
                                if (!collapsed) {
                                    items(
                                        items = section.items,
                                        key = { it.id }
                                    ) { item ->
                                        InspectionItemCard(
                                            item = item,
                                            response = uiState.responses[item.id],
                                            attachmentsById = attachmentsById,
                                            uploadProgress = uploadProgress,
                                            onResponseChange = { status, severity, note ->
                                                viewModel.updateResponse(item.id, status, severity, note)
                                            },
                                            onActualQuantityChange = { qty ->
                                                viewModel.updateActualQuantity(item.id, qty)
                                            },
                                            onTakePhoto = {
                                                currentPickingItemId = item.id
                                                mediaPicker.launchCamera()
                                            },
                                            onChoosePhoto = {
                                                currentPickingItemId = item.id
                                                mediaPicker.launchGallery()
                                            },
                                            onRetryAttachment = viewModel::retryAttachment
                                        )
                                    }
                                }
                            }
                        }

                        Surface(
                            tonalElevation = 4.dp,
                            shadowElevation = 8.dp
                        ) {
                            Button(
                                onClick = viewModel::submit,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                enabled = !uiState.isSubmitting && uiState.isValid
                            ) {
                                if (uiState.isSubmitting) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    Text("Submit Inspection")
                                }
                            }
                        }
                    }
                } else if (uiState.error != null) {
                    Text("Error: ${uiState.error}", modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderCard(
    section: InspectionChecklistSections.Section,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onMarkAllPresent: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(section.category, style = MaterialTheme.typography.titleMedium)
                    Text(section.progressLabel, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onToggle) {
                    Text(if (collapsed) "Expand" else "Collapse")
                }
            }
            if (!collapsed) {
                TextButton(onClick = onMarkAllPresent) {
                    Text("Mark section all present")
                }
            }
        }
    }
}

@Composable
private fun VehicleStatusCard(
    odometerMiles: Int?,
    fluidOil: String?,
    fluidTransmission: String?,
    fluidFuel: String?,
    fluidAntifreeze: String?,
    fluidPowerSteering: String?,
    onOdometerChange: (Int?) -> Unit,
    onFluidChange: (String?, String?, String?, String?, String?) -> Unit
) {
    var milesText by remember(odometerMiles) { mutableStateOf(odometerMiles?.toString().orEmpty()) }
    var oil by remember(fluidOil) { mutableStateOf(fluidOil.orEmpty()) }
    var tran by remember(fluidTransmission) { mutableStateOf(fluidTransmission.orEmpty()) }
    var fuel by remember(fluidFuel) { mutableStateOf(fluidFuel.orEmpty()) }
    var anti by remember(fluidAntifreeze) { mutableStateOf(fluidAntifreeze.orEmpty()) }
    var ps by remember(fluidPowerSteering) { mutableStateOf(fluidPowerSteering.orEmpty()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Vehicle status", style = MaterialTheme.typography.titleMedium)
            Text(
                "Optional — matches Inventory Checkoff header (mileage and fluids).",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = milesText,
                onValueChange = { value ->
                    milesText = value.filter { it.isDigit() }
                    onOdometerChange(milesText.toIntOrNull())
                },
                label = { Text("Mileage") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FluidField("Oil", oil, Modifier.weight(1f)) {
                    oil = it
                    onFluidChange(oil, tran, fuel, anti, ps)
                }
                FluidField("Tran", tran, Modifier.weight(1f)) {
                    tran = it
                    onFluidChange(oil, tran, fuel, anti, ps)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FluidField("Fuel", fuel, Modifier.weight(1f)) {
                    fuel = it
                    onFluidChange(oil, tran, fuel, anti, ps)
                }
                FluidField("Anti.", anti, Modifier.weight(1f)) {
                    anti = it
                    onFluidChange(oil, tran, fuel, anti, ps)
                }
                FluidField("P.S.", ps, Modifier.weight(1f)) {
                    ps = it
                    onFluidChange(oil, tran, fuel, anti, ps)
                }
            }
        }
    }
}

@Composable
private fun FluidField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        placeholder = { Text("F") }
    )
}

@Composable
fun InspectionItemCard(
    item: InspectionTemplateItem,
    response: InspectionResponse?,
    attachmentsById: Map<String, Attachment> = emptyMap(),
    uploadProgress: Map<String, com.example.firestationops.domain.sync.AttachmentUploadProgress> = emptyMap(),
    onResponseChange: (InspectionStatus, DeficiencySeverity?, String?) -> Unit,
    onActualQuantityChange: (Int) -> Unit = {},
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onRetryAttachment: (String) -> Unit = {}
) {
    val expectsQuantity = InspectionValidationRules.requiresActualQuantity(item)
    val incomplete = !InspectionChecklistSections.isItemComplete(item, response)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (incomplete) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.text, style = MaterialTheme.typography.titleMedium)
            item.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            if (expectsQuantity) {
                Spacer(modifier = Modifier.height(12.dp))
                QuantityStepperRow(
                    expected = item.expectedQuantity ?: 0,
                    actual = response?.actualQuantity,
                    onChange = onActualQuantityChange
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InspectionStatusButton(
                    text = "PASS",
                    isSelected = response?.status == InspectionStatus.PASS,
                    onClick = { onResponseChange(InspectionStatus.PASS, null, response?.note) },
                    modifier = Modifier.weight(1f)
                )
                InspectionStatusButton(
                    text = "FAIL",
                    isSelected = response?.status == InspectionStatus.FAIL,
                    onClick = {
                        onResponseChange(
                            InspectionStatus.FAIL,
                            response?.severity ?: DeficiencySeverity.REPAIR_NEEDED,
                            response?.note
                        )
                    },
                    modifier = Modifier.weight(1f),
                    isError = true
                )
                InspectionStatusButton(
                    text = "N/A",
                    isSelected = response?.status == InspectionStatus.NOT_APPLICABLE,
                    onClick = { onResponseChange(InspectionStatus.NOT_APPLICABLE, null, response?.note) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (response?.status == InspectionStatus.FAIL) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Severity", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeficiencySeverityButton(
                        text = "INFO",
                        isSelected = response.severity == DeficiencySeverity.INFORMATIONAL,
                        onClick = { onResponseChange(InspectionStatus.FAIL, DeficiencySeverity.INFORMATIONAL, response.note) },
                        modifier = Modifier.weight(1f)
                    )
                    DeficiencySeverityButton(
                        text = "REPAIR",
                        isSelected = response.severity == DeficiencySeverity.REPAIR_NEEDED || response.severity == null,
                        onClick = { onResponseChange(InspectionStatus.FAIL, DeficiencySeverity.REPAIR_NEEDED, response.note) },
                        modifier = Modifier.weight(1f)
                    )
                    DeficiencySeverityButton(
                        text = "OOS",
                        isSelected = response.severity == DeficiencySeverity.OUT_OF_SERVICE,
                        onClick = { onResponseChange(InspectionStatus.FAIL, DeficiencySeverity.OUT_OF_SERVICE, response.note) },
                        modifier = Modifier.weight(1f),
                        isCritical = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                val isNoteRequired = item.requiresNoteOnFail || response.severity == DeficiencySeverity.OUT_OF_SERVICE
                TextField(
                    value = response.note ?: "",
                    onValueChange = { onResponseChange(InspectionStatus.FAIL, response.severity, it) },
                    label = { Text(if (isNoteRequired) "Note (Required)" else "Note (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = isNoteRequired && response.note.isNullOrBlank()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onTakePhoto,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Take Photo")
                    }
                    OutlinedButton(
                        onClick = onChoosePhoto,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Choose Photo")
                    }
                }

                if (response.attachmentIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    response.attachmentIds.forEach { attachmentId ->
                        val attachment = attachmentsById[attachmentId]
                        AttachmentSyncRow(
                            attachment = attachment,
                            uploadProgress = uploadProgress[attachmentId]?.progressPercent,
                            onRetry = { onRetryAttachment(attachmentId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuantityStepperRow(
    expected: Int,
    actual: Int?,
    onChange: (Int) -> Unit
) {
    val value = actual ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (actual == null) {
                "Found: — / Expected: $expected"
            } else {
                "Found: $actual / Expected: $expected"
            },
            style = MaterialTheme.typography.labelLarge
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onChange((value - 1).coerceAtLeast(0)) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = actual?.toString() ?: "—",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.widthIn(min = 32.dp)
            )
            OutlinedButton(
                onClick = { onChange(value + 1) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
            if (actual == null || actual != expected) {
                TextButton(onClick = { onChange(expected) }) {
                    Text("All present")
                }
            }
        }
    }
}

@Composable
private fun AttachmentSyncRow(
    attachment: Attachment?,
    uploadProgress: Int?,
    onRetry: () -> Unit
) {
    val fileName = attachment?.localUri?.substringAfterLast('/') ?: "Photo"
    val status = attachment?.syncStatus
    val statusLabel = when {
        uploadProgress != null -> "Uploading $uploadProgress%"
        status != null -> PendingSyncQueueBuilder.statusLabel(status)
        else -> "Saving..."
    }
    val statusColor = when {
        uploadProgress != null -> MaterialTheme.colorScheme.primary
        status == SyncStatus.SYNC_FAILED -> MaterialTheme.colorScheme.error
        status == SyncStatus.SYNCED -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(fileName, style = MaterialTheme.typography.bodySmall)
                Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
            }
            if (status == SyncStatus.SYNC_FAILED) {
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
        if (uploadProgress != null) {
            LinearProgressIndicator(
                progress = { uploadProgress / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }
        attachment?.lastError?.let { error ->
            Text(
                error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun DeficiencySeverityButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCritical: Boolean = false
) {
    val containerColor = when {
        isSelected && isCritical -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onSecondary
        isCritical && !isSelected -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 4.dp),
        border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun InspectionStatusButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    val containerColor = when {
        isSelected && isError -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isError && !isSelected -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}
