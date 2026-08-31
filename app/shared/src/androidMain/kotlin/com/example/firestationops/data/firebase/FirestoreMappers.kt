package com.example.firestationops.data.firebase

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.Attachment
import com.example.firestationops.domain.model.CommandLogEntry
import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.DeficiencyStatus
import com.example.firestationops.domain.model.Department
import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.model.IncidentUnitAssignment
import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.InspectionResponse
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.InspectionTemplateItem
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.PersonnelAssignment
import com.example.firestationops.domain.model.Role
import com.example.firestationops.domain.model.Station
import com.example.firestationops.domain.model.SyncStatus
import com.example.firestationops.domain.sync.LegacyFirestoreIdNormalizer
import com.example.firestationops.domain.membership.CalhounMembershipNormalizer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object FirestorePaths {
    fun member(uid: String) = "members/$uid"

    fun department(departmentId: String) = "departments/$departmentId"

    fun departmentMember(departmentId: String, memberId: String) =
        "departments/$departmentId/members/$memberId"

    fun station(departmentId: String, stationId: String) =
        "departments/$departmentId/stations/$stationId"

    fun apparatus(departmentId: String, apparatusId: String) =
        "departments/$departmentId/apparatus/$apparatusId"

    fun template(departmentId: String, templateId: String) =
        "departments/$departmentId/templates/$templateId"

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
        "memberNumber" to member.memberNumber,
        "email" to member.email.lowercase(),
        "firstName" to member.firstName,
        "lastName" to member.lastName,
        "roles" to member.roles.map { it.name },
        "isActive" to member.isActive,
        "createdAt" to member.createdAt,
        "updatedAt" to member.updatedAt
    )

    fun departmentToMap(department: Department): Map<String, Any?> = mapOf(
        "id" to department.id,
        "name" to department.name,
        "stationIds" to department.stationIds,
        "createdAt" to department.createdAt,
        "updatedAt" to department.updatedAt
    )

    fun departmentFromMap(id: String, data: Map<String, Any?>): Department? {
        val name = data["name"] as? String ?: return null
        return Department(
            id = id,
            name = name,
            stationIds = (data["stationIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }

    fun stationToMap(station: Station): Map<String, Any?> = mapOf(
        "id" to station.id,
        "departmentId" to station.departmentId,
        "name" to station.name,
        "address" to station.address,
        "createdAt" to station.createdAt,
        "updatedAt" to station.updatedAt
    )

    fun stationFromMap(id: String, data: Map<String, Any?>): Station? {
        val departmentId = data["departmentId"] as? String ?: return null
        val name = data["name"] as? String ?: return null
        return Station(
            id = id,
            departmentId = departmentId,
            name = name,
            address = data["address"] as? String,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }

    fun apparatusToMap(apparatus: Apparatus): Map<String, Any?> = mapOf(
        "id" to apparatus.id,
        "departmentId" to apparatus.departmentId,
        "stationId" to apparatus.stationId,
        "name" to apparatus.name,
        "type" to apparatus.type,
        "radioName" to apparatus.radioName,
        "status" to apparatus.status.name,
        "year" to apparatus.year,
        "make" to apparatus.make,
        "model" to apparatus.model,
        "vin" to apparatus.vin,
        "licensePlate" to apparatus.licensePlate,
        "createdAt" to apparatus.createdAt,
        "updatedAt" to apparatus.updatedAt
    )

    fun apparatusFromMap(id: String, data: Map<String, Any?>): Apparatus? {
        val departmentId = data["departmentId"] as? String ?: return null
        val stationId = data["stationId"] as? String ?: return null
        val name = data["name"] as? String ?: return null
        val type = data["type"] as? String ?: return null
        val radioName = data["radioName"] as? String ?: return null
        val statusName = data["status"] as? String ?: ApparatusStatus.IN_SERVICE.name
        return Apparatus(
            id = id,
            departmentId = departmentId,
            stationId = LegacyFirestoreIdNormalizer.normalizeEntityId(departmentId, stationId),
            name = name,
            type = type,
            radioName = radioName,
            status = runCatching { ApparatusStatus.valueOf(statusName) }.getOrDefault(ApparatusStatus.IN_SERVICE),
            year = (data["year"] as? Number)?.toInt(),
            make = data["make"] as? String,
            model = data["model"] as? String,
            vin = data["vin"] as? String,
            licensePlate = data["licensePlate"] as? String,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }

    fun templateToMap(template: InspectionTemplate): Map<String, Any?> = mapOf(
        "id" to template.id,
        "departmentId" to template.departmentId,
        "name" to template.name,
        "description" to template.description,
        "apparatusType" to template.apparatusType,
        "version" to template.version,
        "frequencyHours" to template.frequencyHours,
        "isActive" to template.isActive,
        "itemsJson" to json.encodeToString(template.items),
        "createdAt" to template.createdAt,
        "updatedAt" to template.updatedAt
    )

    fun templateFromMap(id: String, data: Map<String, Any?>): InspectionTemplate? {
        val departmentId = data["departmentId"] as? String ?: return null
        val name = data["name"] as? String ?: return null
        val apparatusType = data["apparatusType"] as? String ?: return null
        val itemsJson = data["itemsJson"] as? String ?: "[]"
        val items = runCatching {
            json.decodeFromString<List<InspectionTemplateItem>>(itemsJson)
        }.getOrDefault(emptyList())
        return InspectionTemplate(
            id = id,
            departmentId = departmentId,
            name = name,
            description = data["description"] as? String,
            apparatusType = apparatusType,
            version = (data["version"] as? Number)?.toInt() ?: 1,
            frequencyHours = (data["frequencyHours"] as? Number)?.toInt() ?: 24,
            isActive = data["isActive"] as? Boolean ?: true,
            items = items,
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }

    fun memberFromMap(id: String, data: Map<String, Any?>): Member? {
        val rawDepartmentId = data["departmentId"] as? String ?: return null
        val email = data["email"] as? String ?: return null
        val firstName = data["firstName"] as? String ?: return null
        val lastName = data["lastName"] as? String ?: return null
        val roles = (data["roles"] as? List<*>)?.mapNotNull { roleName ->
            (roleName as? String)?.let { runCatching { Role.valueOf(it) }.getOrNull() }
        }?.toSet() ?: setOf(Role.MEMBER)
        val rawMemberNumber = data["memberNumber"] as? String
        val (departmentId, memberNumber) = CalhounMembershipNormalizer.normalizeDepartmentAndMemberNumber(
            departmentId = rawDepartmentId,
            memberNumber = rawMemberNumber
        )

        return Member(
            id = id,
            departmentId = departmentId,
            email = email,
            firstName = firstName,
            lastName = lastName,
            memberNumber = memberNumber,
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
