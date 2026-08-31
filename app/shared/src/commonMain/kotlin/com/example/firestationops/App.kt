package com.example.firestationops

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource

import firestationops.app.shared.generated.resources.Res
import firestationops.app.shared.generated.resources.compose_multiplatform

import com.example.firestationops.domain.bootstrap.DepartmentCatalogBootstrap
import com.example.firestationops.domain.model.UserState
import com.example.firestationops.domain.repository.mock.MockAuthRepository
import com.example.firestationops.domain.repository.mock.MockApparatusRepository
import com.example.firestationops.domain.repository.mock.MockInspectionRepository
import com.example.firestationops.ui.incident.*
import com.example.firestationops.domain.repository.*
import com.example.firestationops.ui.auth.LoginScreen
import com.example.firestationops.ui.auth.LoginViewModel
import com.example.firestationops.ui.dashboard.DashboardScreen
import com.example.firestationops.ui.dashboard.DashboardViewModel
import com.example.firestationops.ui.inspection.InspectionScreen
import com.example.firestationops.ui.inspection.InspectionViewModel
import com.example.firestationops.ui.deficiency.*
import com.example.firestationops.ui.catalog.CatalogSettingsScreen
import com.example.firestationops.ui.catalog.CatalogSettingsViewModel
import com.example.firestationops.ui.department.DepartmentSettingsScreen
import com.example.firestationops.ui.department.DepartmentSettingsViewModel
import com.example.firestationops.domain.sync.SyncCoordinator
import com.example.firestationops.domain.sync.NoOpSyncCoordinator
import com.example.firestationops.domain.bootstrap.NoOpDepartmentCatalogBootstrap
import com.example.firestationops.domain.model.Role

sealed class Screen {
    object Dashboard : Screen()
    data class Inspection(val apparatusId: String) : Screen()
    object DeficiencyList : Screen()
    data class DeficiencyDetail(val deficiencyId: String) : Screen()
    object IncidentList : Screen()
    data class IncidentDetail(val incidentId: String?) : Screen()
    object DepartmentSettings : Screen()
    object CatalogSettings : Screen()
}

@Composable
fun App(
    authRepository: AuthRepository,
    apparatusRepository: ApparatusRepository,
    inspectionRepository: InspectionRepository,
    deficiencyRepository: DeficiencyRepository,
    attachmentRepository: AttachmentRepository,
    incidentRepository: IncidentRepository,
    departmentRepository: DepartmentRepository,
    memberRosterRepository: MemberRosterRepository = NoOpMemberRosterRepository(),
    catalogAdminRepository: CatalogAdminRepository = NoOpCatalogAdminRepository(),
    departmentCatalogBootstrap: DepartmentCatalogBootstrap = NoOpDepartmentCatalogBootstrap(),
    syncCoordinator: SyncCoordinator = NoOpSyncCoordinator(),
    onRequestBackgroundSync: (String) -> Unit = {},
    onPrepareDepartment: (String) -> Unit = {}
) {
    val loginViewModel = remember { LoginViewModel(authRepository) }
    val userState by loginViewModel.userState.collectAsState()
    
    LaunchedEffect(userState) {
        println("App: Current userState: $userState")
    }

    LaunchedEffect(userState, syncCoordinator) {
        val authenticated = userState as? UserState.Authenticated ?: return@LaunchedEffect
        onPrepareDepartment(authenticated.member.departmentId)
        if (syncCoordinator.isAvailable()) {
            syncCoordinator.syncDepartment(authenticated.member.departmentId)
        }
    }
    
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val state = userState) {
                is UserState.Authenticated -> {
                    when (val screen = currentScreen) {
                        Screen.Dashboard -> {
                            val dashboardViewModel = remember(state.member.departmentId) {
                                DashboardViewModel(
                                    departmentId = state.member.departmentId,
                                    apparatusRepository = apparatusRepository,
                                    deficiencyRepository = deficiencyRepository,
                                    inspectionRepository = inspectionRepository,
                                    attachmentRepository = attachmentRepository,
                                    incidentRepository = incidentRepository,
                                    syncCoordinator = syncCoordinator
                                )
                            }
                            MainContent(
                                member = state.member,
                                onLogout = loginViewModel::logout,
                                content = { 
                                    DashboardScreen(
                                        viewModel = dashboardViewModel,
                                        onApparatusClick = { id -> currentScreen = Screen.Inspection(id) },
                                        onOpenDeficienciesClick = { currentScreen = Screen.DeficiencyList },
                                        onDeficiencyClick = { id -> currentScreen = Screen.DeficiencyDetail(id) },
                                        onOpenIncidentsClick = { currentScreen = Screen.IncidentList },
                                        showDepartmentSettings = state.member.hasRole(Role.OFFICER),
                                        onOpenDepartmentSettings = { currentScreen = Screen.DepartmentSettings },
                                        onSyncNowClick = {
                                            onRequestBackgroundSync(state.member.departmentId)
                                        }
                                    )
                                }
                            )
                        }
                        is Screen.Inspection -> {
                            val inspectionViewModel = remember(screen.apparatusId, state.member.id) {
                                InspectionViewModel(
                                    apparatusId = screen.apparatusId,
                                    member = state.member,
                                    inspectionRepository = inspectionRepository,
                                    deficiencyRepository = deficiencyRepository,
                                    apparatusRepository = apparatusRepository,
                                    attachmentRepository = attachmentRepository
                                )
                            }
                            InspectionScreen(
                                viewModel = inspectionViewModel,
                                onBack = { currentScreen = Screen.Dashboard }
                            )
                        }
                        Screen.DeficiencyList -> {
                            val deficiencyListViewModel = remember(state.member.departmentId) {
                                DeficiencyListViewModel(
                                    departmentId = state.member.departmentId,
                                    deficiencyRepository = deficiencyRepository,
                                    apparatusRepository = apparatusRepository
                                )
                            }
                            DeficiencyListScreen(
                                viewModel = deficiencyListViewModel,
                                onBack = { currentScreen = Screen.Dashboard },
                                onDeficiencyClick = { id -> currentScreen = Screen.DeficiencyDetail(id) }
                            )
                        }
                        is Screen.DeficiencyDetail -> {
                            val deficiencyDetailViewModel = remember(screen.deficiencyId) {
                                DeficiencyDetailViewModel(
                                    deficiencyId = screen.deficiencyId,
                                    userId = state.member.id,
                                    deficiencyRepository = deficiencyRepository,
                                    apparatusRepository = apparatusRepository
                                )
                            }
                            DeficiencyDetailScreen(
                                viewModel = deficiencyDetailViewModel,
                                onBack = { currentScreen = Screen.DeficiencyList },
                                onResolved = { currentScreen = Screen.DeficiencyList }
                            )
                        }
                        Screen.IncidentList -> {
                            val incidentListViewModel = remember(state.member.departmentId) {
                                IncidentListViewModel(
                                    departmentId = state.member.departmentId,
                                    incidentRepository = incidentRepository
                                )
                            }
                            IncidentListScreen(
                                viewModel = incidentListViewModel,
                                onBack = { currentScreen = Screen.Dashboard },
                                onIncidentClick = { id -> currentScreen = Screen.IncidentDetail(id) },
                                onCreateIncident = { currentScreen = Screen.IncidentDetail(null) }
                            )
                        }
                        is Screen.IncidentDetail -> {
                            val incidentDetailViewModel = remember(screen.incidentId, state.member.id) {
                                IncidentDetailViewModel(
                                    incidentId = screen.incidentId,
                                    member = state.member,
                                    incidentRepository = incidentRepository,
                                    apparatusRepository = apparatusRepository,
                                    departmentRepository = departmentRepository
                                )
                            }
                            IncidentDetailScreen(
                                viewModel = incidentDetailViewModel,
                                onBack = { currentScreen = Screen.IncidentList }
                            )
                        }
                        Screen.DepartmentSettings -> {
                            val departmentSettingsViewModel = remember(state.member.id) {
                                DepartmentSettingsViewModel(
                                    member = state.member,
                                    departmentRepository = departmentRepository,
                                    memberRosterRepository = memberRosterRepository,
                                    departmentCatalogBootstrap = departmentCatalogBootstrap,
                                    syncCoordinator = syncCoordinator
                                )
                            }
                            DepartmentSettingsScreen(
                                viewModel = departmentSettingsViewModel,
                                onBack = { currentScreen = Screen.Dashboard },
                                onOpenCatalogSettings = {
                                    if (state.member.hasRole(Role.ADMIN)) {
                                        currentScreen = Screen.CatalogSettings
                                    }
                                }
                            )
                        }
                        Screen.CatalogSettings -> {
                            val catalogSettingsViewModel = remember(state.member.id) {
                                CatalogSettingsViewModel(
                                    member = state.member,
                                    apparatusRepository = apparatusRepository,
                                    inspectionRepository = inspectionRepository,
                                    catalogAdminRepository = catalogAdminRepository,
                                    syncCoordinator = syncCoordinator
                                )
                            }
                            CatalogSettingsScreen(
                                viewModel = catalogSettingsViewModel,
                                onBack = { currentScreen = Screen.DepartmentSettings }
                            )
                        }
                    }
                }
                UserState.Unauthenticated, is UserState.Loading, is UserState.Error -> {
                    LoginScreen(viewModel = loginViewModel)
                }
            }
        }
    }
}

@Composable
fun MainContent(
    member: com.example.firestationops.domain.model.Member,
    onLogout: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
        
        // Simple Bottom Bar for Logout and Info
        Surface(
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeContentPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = buildString {
                            append(member.fullName)
                            member.memberNumber?.let { append(" (#$it)") }
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Dept ${member.departmentId}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Logout")
                }
            }
        }
    }
}

