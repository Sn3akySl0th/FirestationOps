package com.example.firestationops.domain.sync

import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.Incident

object SyncConflictDetector {
    fun isDeficiencyUploadConflict(
        local: Deficiency,
        remote: Deficiency?,
        baseline: Deficiency?
    ): Boolean = isMutableRecordUploadConflict(
        local = local,
        remote = remote,
        baseline = baseline,
        matches = SyncRecordDiffer::deficienciesMatch
    )

    fun isIncidentUploadConflict(
        local: Incident,
        remote: Incident?,
        baseline: Incident?
    ): Boolean = isMutableRecordUploadConflict(
        local = local,
        remote = remote,
        baseline = baseline,
        matches = SyncRecordDiffer::incidentsMatch
    )

    private fun <T> isMutableRecordUploadConflict(
        local: T,
        remote: T?,
        baseline: T?,
        matches: (T, T) -> Boolean
    ): Boolean {
        if (remote == null) return false
        if (matches(local, remote)) return false
        if (baseline == null) return true
        if (matches(baseline, remote)) return false
        if (matches(baseline, local)) return false
        return true
    }
}
