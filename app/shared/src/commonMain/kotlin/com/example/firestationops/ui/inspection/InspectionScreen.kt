package com.example.firestationops.ui.inspection

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.InspectionStatus
import com.example.firestationops.domain.model.InspectionTemplateItem
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
    var currentPickingItemId by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val fileExporter = rememberFileExporter()
    
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
            } else {
                val template = uiState.template
                if (template != null) {
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
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Text(template.name, style = MaterialTheme.typography.titleLarge)
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            items(template.items) { item ->
                                InspectionItemCard(
                                    item = item,
                                    response = uiState.responses[item.id],
                                    onResponseChange = { status, severity, note ->
                                        viewModel.updateResponse(item.id, status, severity, note)
                                    },
                                    onAddAttachment = {
                                        currentPickingItemId = item.id
                                        mediaPicker.launch()
                                    }
                                )
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
fun InspectionItemCard(
    item: InspectionTemplateItem,
    response: com.example.firestationops.domain.model.InspectionResponse?,
    onResponseChange: (InspectionStatus, DeficiencySeverity?, String?) -> Unit,
    onAddAttachment: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(item.text, style = MaterialTheme.typography.titleMedium)
            item.category?.let {
                Text(it, style = MaterialTheme.typography.labelSmall)
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
                
                OutlinedButton(
                    onClick = onAddAttachment,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Photo")
                }
                
                if (response.attachmentIds.isNotEmpty()) {
                    Text("${response.attachmentIds.size} Photos Attached", style = MaterialTheme.typography.labelSmall)
                }
            }
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
