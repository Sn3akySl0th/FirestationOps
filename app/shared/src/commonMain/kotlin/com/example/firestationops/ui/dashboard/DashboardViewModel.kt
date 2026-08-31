package com.example.firestationops.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firestationops.currentTimeMillis
import com.example.firestationops.domain.InspectionComplianceCalculator
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusInspectionStatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.DeficiencySummary
import com.example.firestationops.domain.model.InspectionComplianceStatus
import com.example.firestationops.domain.model.Station
import com.example.firestationops.domain.repository.ApparatusRepository
import com.example.firestationops.domain.repository.AttachmentRepository
import com.example.firestationops.domain.repository.DeficiencyRepository
import com.example.firestationops.domain.repository.IncidentRepository
import com.example.firestationops.domain.repository.InspectionRepository
import com.example.firestationops.domain.repository.SyncConflictRepository
import com.example.firestationops.domain.sync.PendingSyncQueueBuilder
import com.example.firestationops.domain.sync.SyncCoordinator
import com.example.firestationops.domain.sync.SyncMessageFormatter
import com.example.firestationops.domain.sync.SyncQueueState
import com.example.firestationops.domain.sync.SyncRunnerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.example.firestationops.ui.deficiency.DeficiencyWithApparatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(
    private val departmentId: String,
    private val apparatusRepository: ApparatusRepository,
    private val deficiencyRepository: DeficiencyRepository,
    private val inspectionRepository: InspectionRepository,
    private val attachmentRepository: AttachmentRepository,
    private val incidentRepository: IncidentRepository,
    private val syncConflictRepository: SyncConflictRepository,
    private val syncCoordinator: SyncCoordinator,
    private val nowMillis: () -> Long = { currentTimeMillis() }
) : ViewModel() {

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()
    private val _lastSyncResult = MutableStateFlow<com.example.firestationops.domain.sync.SyncResult?>(null)
    val lastSyncResult: StateFlow<com.example.firestationops.domain.sync.SyncResult?> = _lastSyncResult.asStateFlow()
    val syncState: StateFlow<SyncRunnerState> = syncCoordinator.syncState
    val cloudSyncEnabled: Boolean = syncCoordinator.isAvailable()

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            apparatusRepository.getStations(departmentId),
            apparatusRepository.getApparatusByDepartment(departmentId),
            deficiencyRepository.getOpenDeficiencies(departmentId),
            deficiencyRepository.getDeficienciesByDepartment(departmentId)
        ) { stations, apparatusList, openDeficiencies, allDeficiencies ->
            DashboardStationData(stations, apparatusList, openDeficiencies, allDeficiencies)
        },
        combine(
            inspectionRepository.getInspectionsByDepartment(departmentId),
            inspectionRepository.getActiveTemplates(departmentId),
            attachmentRepository.getAttachmentsByDepartment(departmentId)
        ) { inspections, templates, attachments ->
            Triple(inspections, templates, attachments)
        },
        incidentRepository.getIncidentsByDepartment(departmentId),
        syncConflictRepository.getConflictsByDepartment(departmentId)
    ) { stationData, inspectionData, incidents, syncConflicts ->
        val (stations, apparatusList, openDeficiencies, allDeficiencies) = stationData
        val (inspections, templates, attachments) = inspectionData
        buildDashboardState(
            stations = stations,
            apparatusList = apparatusList,
            openDeficiencies = openDeficiencies,
            allDeficiencies = allDeficiencies,
            inspections = inspections,
            templates = templates,
            attachments = attachments,
            incidents = incidents,
            syncConflictCount = syncConflicts.size
        )
    }
        .catch { emit(DashboardUiState.Error(it.message ?: "Failed to load dashboard")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState.Loading)

    fun syncNow() {
        if (!syncCoordinator.isAvailable()) {
            _syncMessage.value = "Cloud sync is not configured on this device."
            return
        }

        viewModelScope.launch {
            _syncMessage.value = null
            val result = syncCoordinator.syncDepartment(departmentId)
            _lastSyncResult.value = result
            _syncMessage.value = SyncMessageFormatter.format(result)
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun retryAttachmentUpload(attachmentId: String) {
        if (!syncCoordinator.isAvailable()) {
            _syncMessage.value = "Cloud sync is not configured on this device."
            return
        }

        viewModelScope.launch {
            attachmentRepository.retryUpload(attachmentId)
            syncNow()
        }
    }

    fun retryAllFailedAttachments() {
        if (!syncCoordinator.isAvailable()) {
            _syncMessage.value = "Cloud sync is not configured on this device."
            return
        }

        viewModelScope.launch {
            attachmentRepository.getAttachmentsByDepartment(departmentId)
                .first()
                .filter { it.syncStatus == com.example.firestationops.domain.model.SyncStatus.SYNC_FAILED }
                .forEach { attachmentRepository.retryUpload(it.id) }
            syncNow()
        }
    }

    private fun buildDashboardState(
        stations: List<Station>,
        apparatusList: List<Apparatus>,
        openDeficiencies: List<Deficiency>,
        allDeficiencies: List<Deficiency>,
        inspections: List<com.example.firestationops.domain.model.Inspection>,
        templates: List<com.example.firestationops.domain.model.InspectionTemplate>,
        attachments: List<com.example.firestationops.domain.model.Attachment>,
        incidents: List<com.example.firestationops.domain.model.Incident>,
        syncConflictCount: Int
    ): DashboardUiState {
        val apparatusMap = apparatusList.associateBy { it.id }
        val complianceStatuses = InspectionComplianceCalculator.calculateForDepartment(
            apparatusList = apparatusList,
            templates = templates,
            inspections = inspections,
            nowMillis = nowMillis()
        )
        val complianceByApparatus = complianceStatuses.associateBy { it.apparatusId }

        val overdueInspections = complianceStatuses
            .filter {
                it.status == InspectionComplianceStatus.OVERDUE ||
                    it.status == InspectionComplianceStatus.NEVER_INSPECTED
            }
            .sortedWith(compareByDescending<ApparatusInspectionStatus> { it.daysOverdue }
                .thenBy { apparatusMap[it.apparatusId]?.radioName ?: "" })
            .mapNotNull { status ->
                apparatusMap[status.apparatusId]?.let { apparatus ->
                    OverdueInspectionItem(
                        apparatus = apparatus,
                        compliance = status
                    )
                }
            }

        val deficiencySummary = buildDeficiencySummary(openDeficiencies)
        val topDeficiencies = openDeficiencies
            .sortedWith(deficiencyPriorityComparator())
            .take(5)
            .map { deficiency ->
                DeficiencyWithApparatus(deficiency, apparatusMap[deficiency.apparatusId])
            }

        val syncQueue = PendingSyncQueueBuilder.build(
            PendingSyncQueueBuilder.Input(
                inspections = inspections,
                deficiencies = allDeficiencies,
                attachments = attachments,
                incidents = incidents,
                apparatusById = apparatusMap,
                templatesById = templates.associateBy { it.id }
            )
        )
        val pendingSyncCount = syncQueue.pendingItems.size

        val stationSections = stations.map { station ->
            StationDashboardSection(
                station = station,
                apparatus = apparatusList
                    .filter { it.stationId == station.id }
                    .map { apparatus ->
                        ApparatusDashboardItem(
                            apparatus = apparatus,
                            compliance = complianceByApparatus[apparatus.id]
                        )
                    }
            )
        }

        return DashboardUiState.Success(
            summary = DashboardSummary(
                overdueCount = overdueInspections.size,
                dueSoonCount = complianceStatuses.count { it.status == InspectionComplianceStatus.DUE_SOON },
                openDeficiencyCount = openDeficiencies.size,
                outOfServiceApparatusCount = apparatusList.count { it.status == ApparatusStatus.OUT_OF_SERVICE },
                pendingSyncCount = pendingSyncCount,
                syncConflictCount = syncConflictCount
            ),
            deficiencySummary = deficiencySummary,
            stations = stationSections,
            overdueInspections = overdueInspections,
            topDeficiencies = topDeficiencies,
            pendingSyncCount = pendingSyncCount,
            syncConflictCount = syncConflictCount,
            syncQueue = syncQueue
        )
    }

    private fun buildDeficiencySummary(openDeficiencies: List<Deficiency>): DeficiencySummary {
        return DeficiencySummary(
            total = openDeficiencies.size,
            outOfService = openDeficiencies.count { it.severity == DeficiencySeverity.OUT_OF_SERVICE },
            repairNeeded = openDeficiencies.count { it.severity == DeficiencySeverity.REPAIR_NEEDED },
            informational = openDeficiencies.count { it.severity == DeficiencySeverity.INFORMATIONAL },
            oldestOpenAt = openDeficiencies.minOfOrNull { it.createdAt }
        )
    }

    companion object {
        fun deficiencyPriorityComparator(): Comparator<Deficiency> = compareBy<Deficiency> {
            when (it.severity) {
                DeficiencySeverity.OUT_OF_SERVICE -> 0
                DeficiencySeverity.REPAIR_NEEDED -> 1
                DeficiencySeverity.INFORMATIONAL -> 2
            }
        }.thenBy { it.createdAt }
    }
}

sealed interface DashboardUiState {
    object Loading : DashboardUiState
    data class Success(
        val summary: DashboardSummary,
        val deficiencySummary: DeficiencySummary,
        val stations: List<StationDashboardSection>,
        val overdueInspections: List<OverdueInspectionItem>,
        val topDeficiencies: List<DeficiencyWithApparatus>,
        val pendingSyncCount: Int,
        val syncConflictCount: Int,
        val syncQueue: SyncQueueState
    ) : DashboardUiState

    data class Error(val message: String) : DashboardUiState
}

data class DashboardSummary(
    val overdueCount: Int,
    val dueSoonCount: Int,
    val openDeficiencyCount: Int,
    val outOfServiceApparatusCount: Int,
    val pendingSyncCount: Int,
    val syncConflictCount: Int = 0
)

data class StationDashboardSection(
    val station: Station,
    val apparatus: List<ApparatusDashboardItem>
)

data class ApparatusDashboardItem(
    val apparatus: Apparatus,
    val compliance: ApparatusInspectionStatus?
)

data class OverdueInspectionItem(
    val apparatus: Apparatus,
    val compliance: ApparatusInspectionStatus
)

private data class DashboardStationData(
    val stations: List<Station>,
    val apparatusList: List<Apparatus>,
    val openDeficiencies: List<Deficiency>,
    val allDeficiencies: List<Deficiency>
)
