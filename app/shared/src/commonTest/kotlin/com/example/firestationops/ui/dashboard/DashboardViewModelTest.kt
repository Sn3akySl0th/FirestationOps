package com.example.firestationops.ui.dashboard

import com.example.firestationops.domain.model.*
import com.example.firestationops.domain.repository.mock.*
import com.example.firestationops.domain.sync.NoOpSyncCoordinator
import com.example.firestationops.domain.sync.PendingSyncRecordType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val departmentId = "mock-dept-id"
    private val apparatusRepository = MockApparatusRepository()
    private val deficiencyRepository = MockDeficiencyRepository()
    private val inspectionRepository = MockInspectionRepository()
    private val attachmentRepository = MockAttachmentRepository()
    private val incidentRepository = MockIncidentRepository()
    private val syncCoordinator = NoOpSyncCoordinator()
    private val syncConflictRepository = MockSyncConflictRepository()
    private val fixedNow = 1_000_000_000_000L

    @Test
    fun aggregatesOverdueCount_whenInspectionIsStale() = runTest {
        val twoDaysAgo = fixedNow - (48 * 3_600_000L)
        inspectionRepository.saveInspection(
            Inspection(
                id = "insp-old",
                templateId = "tmpl-engine",
                apparatusId = "ap-1",
                departmentId = departmentId,
                startedAt = twoDaysAgo,
                completedAt = twoDaysAgo,
                startedByUserId = "admin-1",
                isFinalized = true
            )
        )

        val viewModel = DashboardViewModel(
            departmentId = departmentId,
            apparatusRepository = apparatusRepository,
            deficiencyRepository = deficiencyRepository,
            inspectionRepository = inspectionRepository,
            attachmentRepository = attachmentRepository,
            incidentRepository = incidentRepository,
            syncConflictRepository = syncConflictRepository,
            syncCoordinator = syncCoordinator,
            nowMillis = { fixedNow }
        )

        val state = viewModel.uiState.first { it is DashboardUiState.Success } as DashboardUiState.Success
        assertTrue(state.summary.overdueCount >= 1)
        assertTrue(state.overdueInspections.any { it.apparatus.id == "ap-1" })
    }

    @Test
    fun deficiencySeverityBreakdown_matchesOpenDeficiencies() = runTest {
        deficiencyRepository.saveDeficiency(
            Deficiency(
                id = "def-1",
                apparatusId = "ap-3",
                departmentId = departmentId,
                title = "Pump leak",
                description = "Leak",
                severity = DeficiencySeverity.OUT_OF_SERVICE,
                status = DeficiencyStatus.OPEN,
                createdAt = fixedNow,
                createdByUserId = "admin-1"
            )
        )
        deficiencyRepository.saveDeficiency(
            Deficiency(
                id = "def-2",
                apparatusId = "ap-1",
                departmentId = departmentId,
                title = "Light out",
                description = "Replace bulb",
                severity = DeficiencySeverity.REPAIR_NEEDED,
                status = DeficiencyStatus.OPEN,
                createdAt = fixedNow,
                createdByUserId = "admin-1"
            )
        )

        val viewModel = DashboardViewModel(
            departmentId = departmentId,
            apparatusRepository = apparatusRepository,
            deficiencyRepository = deficiencyRepository,
            inspectionRepository = inspectionRepository,
            attachmentRepository = attachmentRepository,
            incidentRepository = incidentRepository,
            syncConflictRepository = syncConflictRepository,
            syncCoordinator = syncCoordinator,
            nowMillis = { fixedNow }
        )

        val state = viewModel.uiState.first { it is DashboardUiState.Success } as DashboardUiState.Success
        assertEquals(2, state.deficiencySummary.total)
        assertEquals(1, state.deficiencySummary.outOfService)
        assertEquals(1, state.deficiencySummary.repairNeeded)
    }

    @Test
    fun outOfServiceApparatusCount_matchesApparatusStatus() = runTest {
        apparatusRepository.updateApparatusStatus("ap-3", ApparatusStatus.OUT_OF_SERVICE)

        val viewModel = DashboardViewModel(
            departmentId = departmentId,
            apparatusRepository = apparatusRepository,
            deficiencyRepository = deficiencyRepository,
            inspectionRepository = inspectionRepository,
            attachmentRepository = attachmentRepository,
            incidentRepository = incidentRepository,
            syncConflictRepository = syncConflictRepository,
            syncCoordinator = syncCoordinator,
            nowMillis = { fixedNow }
        )

        val state = viewModel.uiState.first { it is DashboardUiState.Success } as DashboardUiState.Success
        assertEquals(1, state.summary.outOfServiceApparatusCount)
    }

    @Test
    fun syncQueue_listsPendingInspectionsOnDashboard() = runTest {
        inspectionRepository.saveInspection(
            Inspection(
                id = "insp-pending",
                templateId = "tmpl-engine",
                apparatusId = "ap-1",
                departmentId = departmentId,
                startedAt = fixedNow,
                startedByUserId = "admin-1",
                syncStatus = SyncStatus.PENDING_SYNC
            )
        )

        val viewModel = DashboardViewModel(
            departmentId = departmentId,
            apparatusRepository = apparatusRepository,
            deficiencyRepository = deficiencyRepository,
            inspectionRepository = inspectionRepository,
            attachmentRepository = attachmentRepository,
            incidentRepository = incidentRepository,
            syncConflictRepository = syncConflictRepository,
            syncCoordinator = syncCoordinator,
            nowMillis = { fixedNow }
        )

        val state = viewModel.uiState.first { it is DashboardUiState.Success } as DashboardUiState.Success
        assertEquals(1, state.pendingSyncCount)
        assertEquals(1, state.syncQueue.pendingItems.size)
        assertEquals(PendingSyncRecordType.INSPECTION, state.syncQueue.pendingItems.single().recordType)
    }
}
