package com.example.firestationops.ui.sync

import com.example.firestationops.domain.repository.DeficiencyRepository
import com.example.firestationops.domain.repository.IncidentRepository
import com.example.firestationops.domain.repository.InspectionRepository
import com.example.firestationops.domain.repository.SyncConflictRepository
import com.example.firestationops.domain.sync.SyncConflict
import com.example.firestationops.domain.sync.SyncConflictRecordType
import com.example.firestationops.domain.sync.SyncConflictResolution
import com.example.firestationops.domain.sync.SyncConflictResolutionService
import com.example.firestationops.domain.sync.SyncCoordinator
import com.example.firestationops.domain.sync.SyncRecordSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncConflictItemUi(
    val conflict: SyncConflict,
    val title: String,
    val localSummary: String,
    val remoteSummary: String,
    val isResolving: Boolean = false
)

data class SyncConflictUiState(
    val conflicts: List<SyncConflictItemUi> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null
)

class SyncConflictViewModel(
    private val departmentId: String,
    private val syncConflictRepository: SyncConflictRepository,
    private val deficiencyRepository: DeficiencyRepository,
    private val incidentRepository: IncidentRepository,
    private val inspectionRepository: InspectionRepository,
    private val syncCoordinator: SyncCoordinator,
    private val scope: CoroutineScope
) {
    private val resolutionService = SyncConflictResolutionService(
        syncConflictRepository = syncConflictRepository,
        deficiencyRepository = deficiencyRepository,
        incidentRepository = incidentRepository,
        inspectionRepository = inspectionRepository
    )
    private val resolvingIds = MutableStateFlow<Set<String>>(emptySet())
    private val message = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SyncConflictUiState> = combine(
        syncConflictRepository.getConflictsByDepartment(departmentId),
        resolvingIds,
        message,
        error
    ) { conflicts, activeResolutions, statusMessage, statusError ->
        SyncConflictUiState(
            conflicts = conflicts.map { conflict ->
                SyncConflictItemUi(
                    conflict = conflict,
                    title = conflictTitle(conflict),
                    localSummary = conflictSummary(conflict, isLocal = true),
                    remoteSummary = conflictSummary(conflict, isLocal = false),
                    isResolving = conflict.id in activeResolutions
                )
            },
            isLoading = false,
            error = statusError,
            message = statusMessage
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), SyncConflictUiState())

    fun resolve(conflict: SyncConflict, resolution: SyncConflictResolution) {
        scope.launch {
            resolvingIds.value += conflict.id
            error.value = null
            message.value = null

            val result = resolutionService.applyResolution(conflict, resolution)
            resolvingIds.value -= conflict.id

            result.onSuccess {
                message.value = when (resolution) {
                    SyncConflictResolution.KEEP_LOCAL -> "Kept this device's version. Sync again to upload."
                    SyncConflictResolution.KEEP_REMOTE -> "Kept the cloud version."
                }
                if (syncCoordinator.isAvailable()) {
                    syncCoordinator.syncDepartment(departmentId)
                }
            }.onFailure {
                error.value = it.message ?: "Failed to resolve conflict."
            }
        }
    }

    fun clearMessage() {
        message.value = null
        error.value = null
    }

    private fun conflictTitle(conflict: SyncConflict): String = when (conflict.recordType) {
        SyncConflictRecordType.DEFICIENCY -> {
            SyncRecordSnapshot.decodeDeficiency(conflict.localSnapshotJson)?.title ?: "Deficiency"
        }
        SyncConflictRecordType.INCIDENT -> {
            SyncRecordSnapshot.decodeIncident(conflict.localSnapshotJson)?.title?.ifBlank { "Incident report" }
                ?: "Incident report"
        }
        SyncConflictRecordType.INSPECTION -> {
            val inspection = SyncRecordSnapshot.decodeInspection(conflict.localSnapshotJson)
            "Duplicate inspection · ${inspection?.apparatusId ?: conflict.recordId}"
        }
    }

    private fun conflictSummary(conflict: SyncConflict, isLocal: Boolean): String {
        val snapshotJson = if (isLocal) conflict.localSnapshotJson else conflict.remoteSnapshotJson
        return when (conflict.recordType) {
            SyncConflictRecordType.DEFICIENCY -> {
                val deficiency = SyncRecordSnapshot.decodeDeficiency(snapshotJson) ?: return "Unavailable"
                buildString {
                    append(deficiency.status.name.replace('_', ' ').lowercase())
                    deficiency.resolutionNote?.takeIf { it.isNotBlank() }?.let { note ->
                        append(" · ")
                        append(note)
                    }
                }
            }
            SyncConflictRecordType.INCIDENT -> {
                val incident = SyncRecordSnapshot.decodeIncident(snapshotJson) ?: return "Unavailable"
                buildString {
                    append(incident.status.name.replace('_', ' ').lowercase())
                    if (incident.summary.isNotBlank()) {
                        append(" · ")
                        append(incident.summary.take(80))
                    }
                }
            }
            SyncConflictRecordType.INSPECTION -> {
                val inspection = SyncRecordSnapshot.decodeInspection(snapshotJson) ?: return "Unavailable"
                buildString {
                    append("Completed ")
                    inspection.completedAt?.let { append(formatInspectionTimestamp(it)) }
                    append(" · ")
                    append("${inspection.responses.count { it.status.name == "FAIL" }} failed item(s)")
                }
            }
        }
    }

    private fun formatInspectionTimestamp(completedAt: Long): String {
        val date = Instant.fromEpochMilliseconds(completedAt)
            .toLocalDateTime(TimeZone.currentSystemDefault()).date
        return "${date.monthNumber}/${date.dayOfMonth}/${date.year}"
    }
}
