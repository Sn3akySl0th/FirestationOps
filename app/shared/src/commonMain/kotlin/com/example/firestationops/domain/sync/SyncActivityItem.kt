package com.example.firestationops.domain.sync

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.Attachment
import com.example.firestationops.domain.model.CommandLogEntry
import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.Department
import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.model.IncidentUnitAssignment
import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.PersonnelAssignment
import com.example.firestationops.domain.model.Station
import com.example.firestationops.domain.model.SyncStatus

enum class SyncActivityDirection {
    DOWNLOAD,
    UPLOAD
}

enum class SyncActivityAction {
    NEW,
    UPDATED
}

enum class SyncActivityRecordType(val label: String) {
    DEPARTMENT("Department"),
    STATION("Station"),
    APPARATUS("Apparatus"),
    TEMPLATE("Template"),
    MEMBER("Member"),
    INSPECTION("Inspection"),
    DEFICIENCY("Deficiency"),
    ATTACHMENT("Attachment"),
    INCIDENT("Incident"),
    COMMAND_LOG("Command log"),
    UNIT_ASSIGNMENT("Unit assignment"),
    PERSONNEL_ASSIGNMENT("Personnel assignment")
}

data class SyncActivityItem(
    val direction: SyncActivityDirection,
    val recordType: SyncActivityRecordType,
    val recordId: String,
    val title: String,
    val detail: String?,
    val action: SyncActivityAction
) {
    fun actionLabel(): String = when (action) {
        SyncActivityAction.NEW -> "New"
        SyncActivityAction.UPDATED -> "Updated"
    }
}

object SyncRecordDiffer {
    fun inspectionsMatch(remote: Inspection, local: Inspection): Boolean =
        remote.normalizeForSyncCompare() == local.normalizeForSyncCompare()

    fun deficienciesMatch(remote: Deficiency, local: Deficiency): Boolean =
        remote.normalizeForSyncCompare() == local.normalizeForSyncCompare()

    fun attachmentsMatch(remote: Attachment, local: Attachment): Boolean =
        remote.normalizeForSyncCompare() == local.normalizeForSyncCompare()

    fun incidentsMatch(remote: Incident, local: Incident): Boolean =
        remote.normalizeForSyncCompare() == local.normalizeForSyncCompare()

    fun commandLogEntriesMatch(remote: CommandLogEntry, local: CommandLogEntry): Boolean =
        remote.normalizeForSyncCompare() == local.normalizeForSyncCompare()

    fun unitAssignmentsMatch(remote: IncidentUnitAssignment, local: IncidentUnitAssignment): Boolean =
        remote.normalizeForSyncCompare() == local.normalizeForSyncCompare()

    fun personnelAssignmentsMatch(remote: PersonnelAssignment, local: PersonnelAssignment): Boolean =
        remote.normalizeForSyncCompare() == local.normalizeForSyncCompare()

    fun departmentsMatch(remote: Department, local: Department): Boolean =
        remote.normalizeCatalogForSyncCompare() == local.normalizeCatalogForSyncCompare()

    fun stationsMatch(remote: Station, local: Station): Boolean =
        remote.normalizeCatalogForSyncCompare() == local.normalizeCatalogForSyncCompare()

    fun apparatusMatch(remote: Apparatus, local: Apparatus): Boolean =
        remote.normalizeCatalogForSyncCompare() == local.normalizeCatalogForSyncCompare()

    fun templatesMatch(remote: InspectionTemplate, local: InspectionTemplate): Boolean =
        remote.normalizeCatalogForSyncCompare() == local.normalizeCatalogForSyncCompare()

    fun membersMatch(remote: Member, local: Member): Boolean =
        remote.normalizeMemberForSyncCompare() == local.normalizeMemberForSyncCompare()

    private fun Inspection.normalizeForSyncCompare(): Inspection =
        copy(syncStatus = SyncStatus.SYNCED)

    private fun Deficiency.normalizeForSyncCompare(): Deficiency =
        copy(syncStatus = SyncStatus.SYNCED)

    private fun Attachment.normalizeForSyncCompare(): Attachment =
        copy(syncStatus = SyncStatus.SYNCED)

    private fun Incident.normalizeForSyncCompare(): Incident =
        copy(syncStatus = SyncStatus.SYNCED)

    private fun CommandLogEntry.normalizeForSyncCompare(): CommandLogEntry =
        copy(syncStatus = SyncStatus.SYNCED)

    private fun IncidentUnitAssignment.normalizeForSyncCompare(): IncidentUnitAssignment =
        copy(syncStatus = SyncStatus.SYNCED)

    private fun PersonnelAssignment.normalizeForSyncCompare(): PersonnelAssignment =
        copy(syncStatus = SyncStatus.SYNCED)

    private fun Department.normalizeCatalogForSyncCompare(): Department =
        copy(createdAt = 0, updatedAt = 0)

    private fun Station.normalizeCatalogForSyncCompare(): Station =
        copy(createdAt = 0, updatedAt = 0)

    private fun Apparatus.normalizeCatalogForSyncCompare(): Apparatus =
        copy(createdAt = 0, updatedAt = 0)

    private fun InspectionTemplate.normalizeCatalogForSyncCompare(): InspectionTemplate =
        copy(createdAt = 0, updatedAt = 0)

    private fun Member.normalizeMemberForSyncCompare(): Member =
        copy(
            email = email.lowercase(),
            createdAt = 0,
            updatedAt = 0
        )
}
