package com.example.firestationops.domain.sync

import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.DeficiencyStatus
import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.model.IncidentStatus
import com.example.firestationops.domain.model.IncidentType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncConflictDetectorTest {
    private val baselineDeficiency = deficiency(status = DeficiencyStatus.OPEN, note = "Original")
    private val localDeficiency = deficiency(status = DeficiencyStatus.RESOLVED, note = "Resolved locally")
    private val remoteDeficiency = deficiency(status = DeficiencyStatus.ASSIGNED, note = "Assigned remotely")

    private val baselineIncident = incident(summary = "Baseline")
    private val localIncident = incident(summary = "Local edit")
    private val remoteIncident = incident(summary = "Remote edit")

    @Test
    fun deficiencyUpload_noRemote_isNotConflict() {
        assertFalse(
            SyncConflictDetector.isDeficiencyUploadConflict(
                local = localDeficiency,
                remote = null,
                baseline = baselineDeficiency
            )
        )
    }

    @Test
    fun deficiencyUpload_onlyLocalChanged_isNotConflict() {
        assertFalse(
            SyncConflictDetector.isDeficiencyUploadConflict(
                local = localDeficiency,
                remote = baselineDeficiency,
                baseline = baselineDeficiency
            )
        )
    }

    @Test
    fun deficiencyUpload_bothChanged_isConflict() {
        assertTrue(
            SyncConflictDetector.isDeficiencyUploadConflict(
                local = localDeficiency,
                remote = remoteDeficiency,
                baseline = baselineDeficiency
            )
        )
    }

    @Test
    fun deficiencyUpload_remoteExistsWithoutBaseline_isConflict() {
        assertTrue(
            SyncConflictDetector.isDeficiencyUploadConflict(
                local = localDeficiency,
                remote = remoteDeficiency,
                baseline = null
            )
        )
    }

    @Test
    fun incidentUpload_bothChanged_isConflict() {
        assertTrue(
            SyncConflictDetector.isIncidentUploadConflict(
                local = localIncident,
                remote = remoteIncident,
                baseline = baselineIncident
            )
        )
    }

    private fun deficiency(status: DeficiencyStatus, note: String): Deficiency =
        Deficiency(
            id = "def-1",
            apparatusId = "ap-1",
            departmentId = "dept-1",
            title = "Pump leak",
            description = note,
            severity = DeficiencySeverity.REPAIR_NEEDED,
            status = status,
            createdAt = 1_000L,
            createdByUserId = "member-1"
        )

    private fun incident(summary: String): Incident =
        Incident(
            id = "inc-1",
            departmentId = "dept-1",
            title = "Training incident",
            summary = summary,
            incidentType = IncidentType.TRAINING,
            status = IncidentStatus.ACTIVE,
            createdAt = 1_000L,
            createdByUserId = "member-1",
            updatedAt = 2_000L,
            updatedByUserId = "member-1"
        )
}
