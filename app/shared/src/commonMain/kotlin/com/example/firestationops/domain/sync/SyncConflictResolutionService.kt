package com.example.firestationops.domain.sync

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.domain.repository.DeficiencyRepository
import com.example.firestationops.domain.repository.IncidentRepository
import com.example.firestationops.domain.repository.InspectionRepository
import com.example.firestationops.domain.repository.SyncConflictRepository

class SyncConflictResolutionService(
    private val syncConflictRepository: SyncConflictRepository,
    private val deficiencyRepository: DeficiencyRepository,
    private val incidentRepository: IncidentRepository,
    private val inspectionRepository: InspectionRepository,
    private val nowMillis: () -> Long = { currentTimeMillis() }
) {
    suspend fun applyResolution(
        conflict: SyncConflict,
        resolution: SyncConflictResolution
    ): Result<Unit> = when (conflict.recordType) {
        SyncConflictRecordType.DEFICIENCY -> resolveDeficiency(conflict, resolution)
        SyncConflictRecordType.INCIDENT -> resolveIncident(conflict, resolution)
        SyncConflictRecordType.INSPECTION -> resolveInspection(conflict, resolution)
    }

    private suspend fun resolveDeficiency(
        conflict: SyncConflict,
        resolution: SyncConflictResolution
    ): Result<Unit> {
        val local = SyncRecordSnapshot.decodeDeficiency(conflict.localSnapshotJson)
            ?: return Result.failure(IllegalStateException("Invalid local deficiency snapshot."))
        val remote = SyncRecordSnapshot.decodeDeficiency(conflict.remoteSnapshotJson)
            ?: return Result.failure(IllegalStateException("Invalid remote deficiency snapshot."))

        val resolved = SyncConflictResolver.resolveDeficiency(resolution, local, remote)
        deficiencyRepository.saveDeficiency(resolved)
        if (resolution == SyncConflictResolution.KEEP_REMOTE) {
            syncConflictRepository.saveBaseline(
                recordType = SyncConflictRecordType.DEFICIENCY,
                recordId = resolved.id,
                snapshotJson = SyncRecordSnapshot.encodeDeficiency(resolved)
            )
        }
        syncConflictRepository.deleteConflict(conflict.id)
        return Result.success(Unit)
    }

    private suspend fun resolveIncident(
        conflict: SyncConflict,
        resolution: SyncConflictResolution
    ): Result<Unit> {
        val local = SyncRecordSnapshot.decodeIncident(conflict.localSnapshotJson)
            ?: return Result.failure(IllegalStateException("Invalid local incident snapshot."))
        val remote = SyncRecordSnapshot.decodeIncident(conflict.remoteSnapshotJson)
            ?: return Result.failure(IllegalStateException("Invalid remote incident snapshot."))

        val resolved = SyncConflictResolver.resolveIncident(resolution, local, remote)
        incidentRepository.saveIncident(resolved)
        if (resolution == SyncConflictResolution.KEEP_REMOTE) {
            syncConflictRepository.saveBaseline(
                recordType = SyncConflictRecordType.INCIDENT,
                recordId = resolved.id,
                snapshotJson = SyncRecordSnapshot.encodeIncident(resolved)
            )
        }
        syncConflictRepository.deleteConflict(conflict.id)
        return Result.success(Unit)
    }

    private suspend fun resolveInspection(
        conflict: SyncConflict,
        resolution: SyncConflictResolution
    ): Result<Unit> {
        val local = SyncRecordSnapshot.decodeInspection(conflict.localSnapshotJson)
            ?: return Result.failure(IllegalStateException("Invalid local inspection snapshot."))
        val remote = SyncRecordSnapshot.decodeInspection(conflict.remoteSnapshotJson)
            ?: return Result.failure(IllegalStateException("Invalid remote inspection snapshot."))

        val voidReason = "Superseded by duplicate inspection resolution."
        val result = SyncConflictResolver.resolveInspection(
            resolution = resolution,
            local = local,
            remote = remote,
            voidedAt = nowMillis(),
            voidReason = voidReason
        )

        when (resolution) {
            SyncConflictResolution.KEEP_REMOTE -> deficiencyRepository.voidDeficienciesForInspection(local.id)
            SyncConflictResolution.KEEP_LOCAL -> deficiencyRepository.voidDeficienciesForInspection(remote.id)
        }

        if (result.removeLocal) {
            inspectionRepository.removeUnsyncedInspection(local.id)
            inspectionRepository.saveInspection(result.local)
        } else {
            inspectionRepository.saveInspection(result.remote)
            inspectionRepository.saveInspection(result.local)
        }

        syncConflictRepository.deleteConflict(conflict.id)
        return Result.success(Unit)
    }
}
