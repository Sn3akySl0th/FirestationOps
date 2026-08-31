package com.example.firestationops.ui.department

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.firestationops.domain.membership.MemberProvisioningRules
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepartmentSettingsScreen(
    viewModel: DepartmentSettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val editorState by viewModel.editorState.collectAsState()
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Members", style = MaterialTheme.typography.titleMedium)
                            if (state.canManageRoster) {
                                TextButton(onClick = viewModel::openNewMemberEditor) {
                                    Text("Add member")
                                }
                            }
                        }
                    }

                    if (state.members.isEmpty()) {
                        item {
                            Text(
                                if (state.canManageRoster) {
                                    "No members yet. Add a member to build your department roster."
                                } else {
                                    "No members found locally. Sync after an administrator provisions member profiles."
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        items(state.members) { rosterMember ->
                            MemberCard(
                                member = rosterMember,
                                canManage = state.canManageRoster,
                                onClick = {
                                    if (state.canManageRoster) {
                                        viewModel.openMemberEditor(rosterMember)
                                    }
                                }
                            )
                        }
                    }

                    if (state.cloudSyncEnabled) {
                        item {
                            Text(
                                if (state.canManageRoster) {
                                    "When adding a member, set an initial password. The app creates their sign-in account automatically."
                                } else {
                                    "New members need a member profile before they can sign in."
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    editorState?.let { editor ->
        MemberEditorDialog(
            editor = editor,
            onDismiss = viewModel::closeMemberEditor,
            onSave = viewModel::saveMemberEditor,
            onEmailChange = viewModel::updateEditorEmail,
            onFirstNameChange = viewModel::updateEditorFirstName,
            onLastNameChange = viewModel::updateEditorLastName,
            onMemberNumberChange = viewModel::updateEditorMemberNumber,
            onInitialPasswordChange = viewModel::updateEditorInitialPassword,
            onToggleRole = viewModel::toggleEditorRole,
            onActiveChange = viewModel::updateEditorActive
        )
    }
}

@Composable
private fun MemberCard(
    member: Member,
    canManage: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canManage) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
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
            if (MemberProvisioningRules.isPendingMemberId(member.id)) {
                Text("Pending sign-in", style = MaterialTheme.typography.labelSmall)
            }
            if (!member.isActive) {
                Text("Inactive", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberEditorDialog(
    editor: MemberEditorState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onEmailChange: (String) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onMemberNumberChange: (String) -> Unit,
    onInitialPasswordChange: (String) -> Unit,
    onToggleRole: (Role) -> Unit,
    onActiveChange: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (editor.memberId == null) "Add member" else "Edit member")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = editor.email,
                    onValueChange = onEmailChange,
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editor.isSaving
                )
                OutlinedTextField(
                    value = editor.firstName,
                    onValueChange = onFirstNameChange,
                    label = { Text("First name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editor.isSaving
                )
                OutlinedTextField(
                    value = editor.lastName,
                    onValueChange = onLastNameChange,
                    label = { Text("Last name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editor.isSaving
                )
                OutlinedTextField(
                    value = editor.memberNumber,
                    onValueChange = onMemberNumberChange,
                    label = { Text("Member number (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !editor.isSaving
                )

                if (editor.showInitialPasswordField) {
                    OutlinedTextField(
                        value = editor.initialPassword,
                        onValueChange = onInitialPasswordChange,
                        label = { Text("Initial password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !editor.isSaving
                    )
                    Text(
                        "The member signs in with their email and this password. They can change it later in Firebase.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text("Roles", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Role.entries.forEach { role ->
                        FilterChip(
                            selected = role in editor.roles,
                            onClick = { onToggleRole(role) },
                            label = { Text(role.name) },
                            enabled = !editor.isSaving
                        )
                    }
                }

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
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !editor.isSaving
            ) {
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
