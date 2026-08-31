package com.example.firestationops.domain.sync

import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.SyncStatus

object SyncConflictResolver {
    fun resolveDeficiency(
        resolution: SyncConflictResolution,
        local: Deficiency,
        remote: Deficiency
    ): Deficiency = when (resolution) {
        SyncConflictResolution.KEEP_LOCAL -> local.copy(syncStatus = SyncStatus.PENDING_SYNC)
        SyncConflictResolution.KEEP_REMOTE -> remote.copy(syncStatus = SyncStatus.SYNCED)
    }

    fun resolveIncident(
        resolution: SyncConflictResolution,
        local: Incident,
        remote: Incident
    ): Incident = when (resolution) {
        SyncConflictResolution.KEEP_LOCAL -> local.copy(syncStatus = SyncStatus.PENDING_SYNC)
        SyncConflictResolution.KEEP_REMOTE -> remote.copy(syncStatus = SyncStatus.SYNCED)
    }

    fun resolveInspection(
        resolution: SyncConflictResolution,
        local: Inspection,
        remote: Inspection,
        voidedAt: Long,
        voidReason: String
    ): InspectionResolutionResult = when (resolution) {
        SyncConflictResolution.KEEP_LOCAL -> InspectionResolutionResult(
            local = local.copy(syncStatus = SyncStatus.PENDING_SYNC),
            remote = remote.copy(
                voidedAt = voidedAt,
                voidedReason = voidReason,
                syncStatus = SyncStatus.PENDING_SYNC
            ),
            removeLocal = false
        )
        SyncConflictResolution.KEEP_REMOTE -> InspectionResolutionResult(
            local = remote.copy(syncStatus = SyncStatus.SYNCED),
            remote = remote,
            removeLocal = true
        )
    }
}

data class InspectionResolutionResult(
    val local: Inspection,
    val remote: Inspection,
    val removeLocal: Boolean
)
