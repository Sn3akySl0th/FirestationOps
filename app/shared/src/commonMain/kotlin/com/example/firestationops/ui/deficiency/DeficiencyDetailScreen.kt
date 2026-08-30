package com.example.firestationops.ui.deficiency

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeficiencyDetailScreen(
    viewModel: DeficiencyDetailViewModel,
    onBack: () -> Unit,
    onResolved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var resolutionNote by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is DeficiencyDetailUiState.Resolved) {
            onResolved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deficiency Detail") },
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
                is DeficiencyDetailUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is DeficiencyDetailUiState.Error -> {
                    Text(state.message, modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
                }
                is DeficiencyDetailUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Info Section
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = state.apparatus?.radioName ?: "Unknown Apparatus",
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    text = state.deficiency.title,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                SeverityBadge(state.deficiency.severity)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Reported on: ${formatDate(state.deficiency.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // Description Section
                        Text("Description", style = MaterialTheme.typography.titleMedium)
                        Text(state.deficiency.description, style = MaterialTheme.typography.bodyLarge)

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Resolution Section
                        Text("Resolve Deficiency", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = resolutionNote,
                            onValueChange = { resolutionNote = it },
                            label = { Text("Resolution Note") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            enabled = !state.isResolving
                        )

                        if (state.errorMessage != null) {
                            Text(state.errorMessage, color = MaterialTheme.colorScheme.error)
                        }

                        Button(
                            onClick = { viewModel.resolve(resolutionNote) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = resolutionNote.isNotBlank() && !state.isResolving
                        ) {
                            if (state.isResolving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Mark as Resolved")
                            }
                        }
                    }
                }
                is DeficiencyDetailUiState.Resolved -> {
                    // Handled by LaunchedEffect
                }
            }
        }
    }
}
