package com.example.firestationops.data.firebase

import com.example.firestationops.domain.model.Attachment
import com.example.firestationops.domain.model.CommandLogEntry
import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.DeficiencyStatus
import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.model.IncidentUnitAssignment
import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.InspectionResponse
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.PersonnelAssignment
import com.example.firestationops.domain.model.Role
import com.example.firestationops.domain.model.SyncStatus
import com.example.firestationops.domain.sync.LegacyFirestoreIdNormalizer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object FirestorePaths {
    fun member(uid: String) = "members/$uid"

    fun inspection(departmentId: String, id: String) =
        "departments/$departmentId/inspections/$id"

    fun deficiency(departmentId: String, id: String) =
        "departments/$departmentId/deficiencies/$id"

    fun attachment(departmentId: String, id: String) =
        "departments/$departmentId/attachments/$id"

    fun incident(departmentId: String, id: String) =
        "departments/$departmentId/incidents/$id"

    fun commandLogEntry(departmentId: String, incidentId: String, entryId: String) =
        "departments/$departmentId/incidents/$incidentId/commandLog/$entryId"

    fun unitAssignment(departmentId: String, incidentId: String, assignmentId: String) =
        "departments/$departmentId/incidents/$incidentId/unitAssignments/$assignmentId"

    fun personnelAssignment(departmentId: String, incidentId: String, assignmentId: String) =
        "departments/$departmentId/incidents/$incidentId/personnelAssignments/$assignmentId"

    fun attachmentStorage(departmentId: String, attachmentId: String) =
        "departments/$departmentId/attachments/$attachmentId.jpg"
}

internal object FirestoreMappers {
    private val json = Json { ignoreUnknownKeys = true }

    fun memberToMap(member: Member): Map<String, Any?> = mapOf(
        "id" to member.id,
        "departmentId" to member.departmentId,
        "email" to member.email.lowercase(),
        "firstName" to member.firstName,
        "lastName" to member.lastName,
        "roles" to member.roles.map { it.name },
        "isActive" to member.isActive,
        "createdAt" to member.createdAt,
        "updatedAt" to member.updatedAt
    )

    fun memberFromMap(id: String, data: Map<String, Any?>): Member? {
        val departmentId = data["departmentId"] as? String ?: return null
        val email = data["email"] as? String ?: return null
        val firstName = data["firstName"] as? String ?: return null
        val lastName = data["lastName"] as? String ?: return null
        val roles = (data["roles"] as? List<*>)?.mapNotNull { roleName ->
            (roleName as? String)?.let { runCatching { Role.valueOf(it) }.getOrNull() }
        }?.toSet() ?: setOf(Role.MEMBER)

        return Member(
            id = id,
            departmentId = departmentId,
            email = email,
            firstName = firstName,
            lastName = lastName,
            roles = roles.ifEmpty { setOf(Role.MEMBER) },
            isActive = data["isActive"] as? Boolean ?: true,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }

    fun inspectionToMap(inspection: Inspection): Map<String, Any?> = mapOf(
        "id" to inspection.id,
        "templateId" to inspection.templateId,
        "apparatusId" to inspection.apparatusId,
        "departmentId" to inspection.departmentId,
        "startedAt" to inspection.startedAt,
        "completedAt" to inspection.completedAt,
        "startedByUserId" to inspection.startedByUserId,
        "responsesJson" to json.encodeToString(inspection.responses),
        "isFinalized" to inspection.isFinalized,
        "syncStatus" to SyncStatus.SYNCED.name
    )

    fun inspectionFromMap(id: String, data: Map<String, Any?>): Inspection? {
        val departmentId = data["departmentId"] as? String ?: return null
        val templateId = data["templateId"] as? String ?: return null
        val apparatusId = data["apparatusId"] as? String ?: return null
        val startedAt = (data["startedAt"] as? Number)?.toLong() ?: return null
        val startedByUserId = data["startedByUserId"] as? String ?: return null
        val responsesJson = data["responsesJson"] as? String ?: "[]"
        val responses = runCatching {
            json.decodeFromString<List<InspectionResponse>>(responsesJson)
        }.getOrDefault(emptyList())

        return Inspection(
            id = id,
            templateId = LegacyFirestoreIdNormalizer.normalizeEntityId(departmentId, templateId),
            apparatusId = LegacyFirestoreIdNormalizer.normalizeEntityId(departmentId, apparatusId),
            departmentId = departmentId,
            startedAt = startedAt,
            completedAt = (data["completedAt"] as? Number)?.toLong(),
            startedByUserId = startedByUserId,
            responses = responses,
            isFinalized = data["isFinalized"] as? Boolean ?: false,
            syncStatus = SyncStatus.SYNCED
        )
    }

    fun deficiencyFromMap(id: String, data: Map<String, Any?>): Deficiency? {
        val departmentId = data["departmentId"] as? String ?: return null
        val apparatusId = data["apparatusId"] as? String ?: return null
        val title = data["title"] as? String ?: return null
        val description = data["description"] as? String ?: return null
        val severityName = data["severity"] as? String ?: return null
        val statusName = data["status"] as? String ?: return null
        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: return null
        val createdByUserId = data["createdByUserId"] as? String ?: return null

        return Deficiency(
            id = id,
            inspectionId = data["inspectionId"] as? String,
            apparatusId = LegacyFirestoreIdNormalizer.normalizeEntityId(departmentId, apparatusId),
            departmentId = departmentId,
            title = title,
            description = description,
            severity = runCatching { DeficiencySeverity.valueOf(severityName) }.getOrDefault(DeficiencySeverity.REPAIR_NEEDED),
            status = runCatching { DeficiencyStatus.valueOf(statusName) }.getOrDefault(DeficiencyStatus.OPEN),
            createdAt = createdAt,
            createdByUserId = createdByUserId,
            resolvedAt = (data["resolvedAt"] as? Number)?.toLong(),
            resolvedByUserId = data["resolvedByUserId"] as? String,
            resolutionNote = data["resolutionNote"] as? String,
            syncStatus = SyncStatus.SYNCED,
            attachmentIds = (data["attachmentIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        )
    }

    fun deficiencyToMap(deficiency: Deficiency): Map<String, Any?> = mapOf(
        "id" to deficiency.id,
        "inspectionId" to deficiency.inspectionId,
        "apparatusId" to deficiency.apparatusId,
        "departmentId" to deficiency.departmentId,
        "title" to deficiency.title,
        "description" to deficiency.description,
        "severity" to deficiency.severity.name,
        "status" to deficiency.status.name,
        "createdAt" to deficiency.createdAt,
        "createdByUserId" to deficiency.createdByUserId,
        "resolvedAt" to deficiency.resolvedAt,
        "resolvedByUserId" to deficiency.resolvedByUserId,
        "resolutionNote" to deficiency.resolutionNote,
        "syncStatus" to SyncStatus.SYNCED.name,
        "attachmentIds" to deficiency.attachmentIds
    )

    fun attachmentToMap(attachment: Attachment): Map<String, Any?> = mapOf(
        "id" to attachment.id,
        "departmentId" to attachment.departmentId,
        "localUri" to attachment.localUri,
        "remoteUrl" to attachment.remoteUrl,
        "syncStatus" to SyncStatus.SYNCED.name,
        "createdAt" to attachment.createdAt,
        "createdByUserId" to attachment.createdByUserId
    )

    fun incidentToMap(incident: Incident): Map<String, Any?> = mapOf(
        "id" to incident.id,
        "departmentId" to incident.departmentId,
        "title" to incident.title,
        "summary" to incident.summary,
        "locationDescription" to incident.locationDescription,
        "incidentType" to incident.incidentType.name,
        "status" to incident.status.name,
        "createdAt" to incident.createdAt,
        "createdByUserId" to incident.createdByUserId,
        "updatedAt" to incident.updatedAt,
        "updatedByUserId" to incident.updatedByUserId,
        "closedAt" to incident.closedAt,
        "closedByUserId" to incident.closedByUserId,
        "syncStatus" to SyncStatus.SYNCED.name
    )

    fun commandLogEntryToMap(entry: CommandLogEntry): Map<String, Any?> = mapOf(
        "id" to entry.id,
        "incidentId" to entry.incidentId,
        "departmentId" to entry.departmentId,
        "message" to entry.message,
        "entryType" to entry.entryType.name,
        "createdAt" to entry.createdAt,
        "createdByUserId" to entry.createdByUserId,
        "incidentTimestamp" to entry.incidentTimestamp,
        "correctsEntryId" to entry.correctsEntryId,
        "syncStatus" to SyncStatus.SYNCED.name
    )

    fun unitAssignmentToMap(assignment: IncidentUnitAssignment): Map<String, Any?> = mapOf(
        "id" to assignment.id,
        "incidentId" to assignment.incidentId,
        "departmentId" to assignment.departmentId,
        "apparatusId" to assignment.apparatusId,
        "status" to assignment.status.name,
        "assignedAt" to assignment.assignedAt,
        "assignedByUserId" to assignment.assignedByUserId,
        "updatedAt" to assignment.updatedAt,
        "updatedByUserId" to assignment.updatedByUserId,
        "syncStatus" to SyncStatus.SYNCED.name
    )

    fun personnelAssignmentToMap(assignment: PersonnelAssignment): Map<String, Any?> = mapOf(
        "id" to assignment.id,
        "incidentId" to assignment.incidentId,
        "departmentId" to assignment.departmentId,
        "memberId" to assignment.memberId,
        "status" to assignment.status.name,
        "assignedAt" to assignment.assignedAt,
        "assignedByUserId" to assignment.assignedByUserId,
        "updatedAt" to assignment.updatedAt,
        "updatedByUserId" to assignment.updatedByUserId,
        "syncStatus" to SyncStatus.SYNCED.name
    )
}
