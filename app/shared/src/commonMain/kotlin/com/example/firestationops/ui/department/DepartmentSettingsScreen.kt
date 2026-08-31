package com.example.firestationops.ui.department

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.firestationops.domain.model.Member

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartmentSettingsScreen(
    viewModel: DepartmentSettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(actionMessage) {
        actionMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearActionMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Department settings") },
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
            DepartmentSettingsUiState.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is DepartmentSettingsUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::refresh) {
                        Text("Retry")
                    }
                }
            }
            is DepartmentSettingsUiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(state.departmentName, style = MaterialTheme.typography.titleLarge)
                                Text("Department number: ${state.departmentId}", style = MaterialTheme.typography.bodyMedium)
                                if (state.cloudSyncEnabled) {
                                    Text(
                                        if (state.cloudCatalogEmpty) {
                                            "Cloud catalog is empty. An administrator can bootstrap stations, apparatus, and templates."
                                        } else {
                                            "Cloud catalog is configured. Sync now to refresh local records."
                                        },
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }

                    if (state.cloudSyncEnabled && state.canBootstrapCatalog && state.cloudCatalogEmpty) {
                        item {
                            Button(
                                onClick = viewModel::bootstrapCatalog,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Bootstrap demo catalog to cloud")
                            }
                        }
                    }

                    item {
                        Text("Members", style = MaterialTheme.typography.titleMedium)
                    }

                    if (state.members.isEmpty()) {
                        item {
                            Text(
                                "No members found locally. Sync after an administrator provisions member profiles in Firebase.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        items(state.members) { member ->
                            MemberCard(member)
                        }
                    }

                    if (state.cloudSyncEnabled) {
                        item {
                            Text(
                                "New members need a Firebase Authentication account and a members/{uid} profile document before they can sign in.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberCard(member: Member) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(member.fullName, style = MaterialTheme.typography.titleMedium)
            member.memberNumber?.let { number ->
                Text("Member #$number", style = MaterialTheme.typography.bodySmall)
            }
            Text(member.email, style = MaterialTheme.typography.bodySmall)
            Text(
                member.roles.joinToString { it.name },
                style = MaterialTheme.typography.labelMedium
            )
            if (!member.isActive) {
                Text("Inactive", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
