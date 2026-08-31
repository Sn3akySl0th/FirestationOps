package com.example.firestationops.db

import app.cash.sqldelight.db.SqlDriver
import com.example.firestationops.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FirestationOpsDatabase(driver: SqlDriver) {
    private val database = FirestationOpsDb(driver)
    private val dbQueries = database.firestationOpsQueries

    // Stations
    fun getAllStations(): List<Station> = dbQueries.selectAllStations().executeAsList().map { 
        Station(id = it.id, departmentId = it.departmentId, name = it.name, address = it.address)
    }

    fun insertStation(station: Station) {
        dbQueries.insertStation(id = station.id, departmentId = station.departmentId, name = station.name, address = station.address)
    }

    // Apparatus
    fun getAllApparatus(): List<Apparatus> = dbQueries.selectAllApparatus().executeAsList().map {
        Apparatus(
            id = it.id, 
            departmentId = it.departmentId, 
            stationId = it.stationId, 
            name = it.name, 
            type = it.type, 
            radioName = it.radioName, 
            status = ApparatusStatus.valueOf(it.status)
        )
    }

    fun insertApparatus(apparatus: Apparatus) {
        dbQueries.insertApparatus(
            id = apparatus.id, 
            departmentId = apparatus.departmentId, 
            stationId = apparatus.stationId, 
            name = apparatus.name, 
            type = apparatus.type, 
            radioName = apparatus.radioName, 
            status = apparatus.status.name
        )
    }

    // Templates
    fun getAllTemplates(): List<InspectionTemplate> = dbQueries.selectAllTemplates().executeAsList().map {
        InspectionTemplate(
            id = it.id,
            departmentId = it.departmentId,
            name = it.name,
            apparatusType = it.apparatusType,
            items = Json.decodeFromString(it.itemsJson),
            frequencyHours = it.frequencyHours.toInt(),
            isActive = it.isActive.toInt() == 1
        )
    }

    fun insertTemplate(template: InspectionTemplate) {
        dbQueries.insertTemplate(
            id = template.id, 
            departmentId = template.departmentId, 
            name = template.name, 
            apparatusType = template.apparatusType, 
            itemsJson = Json.encodeToString(template.items),
            frequencyHours = template.frequencyHours.toLong(),
            isActive = if (template.isActive) 1 else 0
        )
    }

    // Inspections
    fun getAllInspections(): List<Inspection> = 
        dbQueries.selectAllInspections().executeAsList().map {
            Inspection(
                id = it.id,
                templateId = it.templateId,
                apparatusId = it.apparatusId,
                departmentId = it.departmentId,
                startedAt = it.startedAt,
                completedAt = it.completedAt,
                startedByUserId = it.startedByUserId,
                responses = Json.decodeFromString(it.responsesJson),
                isFinalized = it.isFinalized.toInt() == 1,
                syncStatus = SyncStatus.valueOf(it.syncStatus)
            )
        }

    fun getInspectionsForApparatus(apparatusId: String): List<Inspection> = 
        dbQueries.selectInspectionsByApparatus(apparatusId).executeAsList().map {
            Inspection(
                id = it.id,
                templateId = it.templateId,
                apparatusId = it.apparatusId,
                departmentId = it.departmentId,
                startedAt = it.startedAt,
                completedAt = it.completedAt,
                startedByUserId = it.startedByUserId,
                responses = Json.decodeFromString(it.responsesJson),
                isFinalized = it.isFinalized.toInt() == 1,
                syncStatus = SyncStatus.valueOf(it.syncStatus)
            )
        }

    fun getLatestDraftByApparatus(apparatusId: String): Inspection? =
        dbQueries.selectLatestDraftByApparatus(apparatusId).executeAsOneOrNull()?.let {
            Inspection(
                id = it.id,
                templateId = it.templateId,
                apparatusId = it.apparatusId,
                departmentId = it.departmentId,
                startedAt = it.startedAt,
                completedAt = it.completedAt,
                startedByUserId = it.startedByUserId,
                responses = Json.decodeFromString(it.responsesJson),
                isFinalized = it.isFinalized.toInt() == 1,
                syncStatus = SyncStatus.valueOf(it.syncStatus)
            )
        }

    private fun mapInspectionRow(it: com.example.firestationops.db.InspectionEntity): Inspection =
        Inspection(
            id = it.id,
            templateId = it.templateId,
            apparatusId = it.apparatusId,
            departmentId = it.departmentId,
            startedAt = it.startedAt,
            completedAt = it.completedAt,
            startedByUserId = it.startedByUserId,
            responses = Json.decodeFromString(it.responsesJson),
            isFinalized = it.isFinalized.toInt() == 1,
            syncStatus = SyncStatus.valueOf(it.syncStatus)
        )

    fun getInspectionsByDepartment(departmentId: String): List<Inspection> =
        dbQueries.selectInspectionsByDepartment(departmentId).executeAsList().map { row ->
            mapInspectionRow(row)
        }

    fun getLatestFinalizedByApparatus(apparatusId: String): Inspection? =
        dbQueries.selectLatestFinalizedByApparatus(apparatusId).executeAsOneOrNull()?.let { row ->
            Inspection(
                id = row.id,
                templateId = row.templateId,
                apparatusId = row.apparatusId,
                departmentId = row.departmentId,
                startedAt = row.startedAt,
                completedAt = row.completedAt,
                startedByUserId = row.startedByUserId,
                responses = Json.decodeFromString(row.responsesJson),
                isFinalized = row.isFinalized.toInt() == 1,
                syncStatus = SyncStatus.valueOf(row.syncStatus)
            )
        }

    fun insertInspection(inspection: Inspection) {
        dbQueries.insertInspection(
            id = inspection.id,
            templateId = inspection.templateId,
            apparatusId = inspection.apparatusId,
            departmentId = inspection.departmentId,
            startedAt = inspection.startedAt,
            completedAt = inspection.completedAt,
            startedByUserId = inspection.startedByUserId,
            responsesJson = Json.encodeToString(inspection.responses),
            isFinalized = if (inspection.isFinalized) 1 else 0,
            syncStatus = inspection.syncStatus.name
        )
    }

    fun updateInspectionSyncStatus(id: String, syncStatus: SyncStatus) {
        dbQueries.updateInspectionSyncStatus(syncStatus = syncStatus.name, id = id)
    }

    fun getPendingSyncInspections(): List<Inspection> =
        dbQueries.selectPendingSyncInspections().executeAsList().map {
            Inspection(
                id = it.id,
                templateId = it.templateId,
                apparatusId = it.apparatusId,
                departmentId = it.departmentId,
                startedAt = it.startedAt,
                completedAt = it.completedAt,
                startedByUserId = it.startedByUserId,
                responses = Json.decodeFromString(it.responsesJson),
                isFinalized = it.isFinalized.toInt() == 1,
                syncStatus = SyncStatus.valueOf(it.syncStatus)
            )
        }

    // Deficiencies
    fun getAllDeficiencies(): List<Deficiency> =
        dbQueries.selectAllDeficiencies().executeAsList().map {
            Deficiency(
                id = it.id, 
                inspectionId = it.inspectionId, 
                apparatusId = it.apparatusId, 
                departmentId = it.departmentId,
                title = it.title, 
                description = it.description, 
                severity = DeficiencySeverity.valueOf(it.severity),
                status = DeficiencyStatus.valueOf(it.status), 
                createdAt = it.createdAt, 
                createdByUserId = it.createdByUserId,
                resolvedAt = it.resolvedAt, 
                resolvedByUserId = it.resolvedByUserId, 
                resolutionNote = it.resolutionNote,
                syncStatus = SyncStatus.valueOf(it.syncStatus),
                attachmentIds = Json.decodeFromString(it.attachmentIdsJson)
            )
        }

    fun getDeficienciesByDepartment(departmentId: String): List<Deficiency> =
        dbQueries.selectDeficienciesByDepartment(departmentId).executeAsList().map {
            Deficiency(
                id = it.id, 
                inspectionId = it.inspectionId, 
                apparatusId = it.apparatusId, 
                departmentId = it.departmentId,
                title = it.title, 
                description = it.description, 
                severity = DeficiencySeverity.valueOf(it.severity),
                status = DeficiencyStatus.valueOf(it.status), 
                createdAt = it.createdAt, 
                createdByUserId = it.createdByUserId,
                resolvedAt = it.resolvedAt, 
                resolvedByUserId = it.resolvedByUserId, 
                resolutionNote = it.resolutionNote,
                syncStatus = SyncStatus.valueOf(it.syncStatus),
                attachmentIds = Json.decodeFromString(it.attachmentIdsJson)
            )
        }

    fun insertDeficiency(deficiency: Deficiency) {
        dbQueries.insertDeficiency(
            id = deficiency.id, 
            inspectionId = deficiency.inspectionId, 
            apparatusId = deficiency.apparatusId, 
            departmentId = deficiency.departmentId,
            title = deficiency.title, 
            description = deficiency.description, 
            severity = deficiency.severity.name,
            status = deficiency.status.name, 
            createdAt = deficiency.createdAt, 
            createdByUserId = deficiency.createdByUserId,
            resolvedAt = deficiency.resolvedAt, 
            resolvedByUserId = deficiency.resolvedByUserId, 
            resolutionNote = deficiency.resolutionNote,
            syncStatus = deficiency.syncStatus.name,
            attachmentIdsJson = Json.encodeToString(deficiency.attachmentIds)
        )
    }

    fun updateDeficiencyStatus(id: String, status: DeficiencyStatus, resolvedAt: Long?, resolvedByUserId: String?, resolutionNote: String?) {
        dbQueries.updateDeficiencyStatus(
            status = status.name,
            resolvedAt = resolvedAt,
            resolvedByUserId = resolvedByUserId,
            resolutionNote = resolutionNote,
            id = id
        )
    }

    fun updateDeficiencySyncStatus(id: String, syncStatus: SyncStatus) {
        dbQueries.updateDeficiencySyncStatus(syncStatus = syncStatus.name, id = id)
    }

    fun getPendingSyncDeficiencies(): List<Deficiency> =
        dbQueries.selectPendingSyncDeficiencies().executeAsList().map {
            Deficiency(
                id = it.id, 
                inspectionId = it.inspectionId, 
                apparatusId = it.apparatusId, 
                departmentId = it.departmentId,
                title = it.title, 
                description = it.description, 
                severity = DeficiencySeverity.valueOf(it.severity),
                status = DeficiencyStatus.valueOf(it.status), 
                createdAt = it.createdAt, 
                createdByUserId = it.createdByUserId,
                resolvedAt = it.resolvedAt, 
                resolvedByUserId = it.resolvedByUserId, 
                resolutionNote = it.resolutionNote,
                syncStatus = SyncStatus.valueOf(it.syncStatus),
                attachmentIds = Json.decodeFromString(it.attachmentIdsJson)
            )
        }

    fun updateApparatusStatus(id: String, status: ApparatusStatus) {
        dbQueries.updateApparatusStatus(status = status.name, id = id)
    }

    fun updateStationDepartmentId(id: String, departmentId: String) {
        dbQueries.updateStationDepartmentId(departmentId = departmentId, id = id)
    }

    fun updateApparatusDepartmentId(id: String, departmentId: String) {
        dbQueries.updateApparatusDepartmentId(departmentId = departmentId, id = id)
    }

    fun updateTemplateDepartmentId(id: String, departmentId: String) {
        dbQueries.updateTemplateDepartmentId(departmentId = departmentId, id = id)
    }

    fun updateInspectionApparatusId(newApparatusId: String, oldApparatusId: String) {
        dbQueries.updateInspectionApparatusId(newApparatusId, oldApparatusId)
    }

    fun updateInspectionTemplateId(newTemplateId: String, oldTemplateId: String) {
        dbQueries.updateInspectionTemplateId(newTemplateId, oldTemplateId)
    }

    fun updateDeficiencyApparatusId(newApparatusId: String, oldApparatusId: String) {
        dbQueries.updateDeficiencyApparatusId(newApparatusId, oldApparatusId)
    }

    fun updateApparatusStationId(newStationId: String, oldStationId: String) {
        dbQueries.updateApparatusStationId(newStationId, oldStationId)
    }

    fun updateUnitAssignmentApparatusId(newApparatusId: String, oldApparatusId: String) {
        dbQueries.updateUnitAssignmentApparatusId(newApparatusId, oldApparatusId)
    }

    fun deleteStationById(id: String) {
        dbQueries.deleteStationById(id)
    }

    fun deleteApparatusById(id: String) {
        dbQueries.deleteApparatusById(id)
    }

    fun deleteTemplateById(id: String) {
        dbQueries.deleteTemplateById(id)
    }

    // Departments
    fun getAllDepartments(): List<Department> = dbQueries.selectAllDepartments().executeAsList().map {
        Department(
            id = it.id,
            name = it.name,
            stationIds = Json.decodeFromString(it.stationIdsJson),
            createdAt = it.createdAt,
            updatedAt = it.updatedAt
        )
    }

    fun getDepartmentById(id: String): Department? = dbQueries.selectDepartmentById(id).executeAsOneOrNull()?.let {
        Department(
            id = it.id,
            name = it.name,
            stationIds = Json.decodeFromString(it.stationIdsJson),
            createdAt = it.createdAt,
            updatedAt = it.updatedAt
        )
    }

    fun insertDepartment(department: Department) {
        dbQueries.insertDepartment(
            id = department.id,
            name = department.name,
            stationIdsJson = Json.encodeToString(department.stationIds),
            createdAt = department.createdAt,
            updatedAt = department.updatedAt
        )
    }

    // Members
    fun getMemberByEmail(email: String): Member? = dbQueries.selectMemberByEmail(email).executeAsOneOrNull()?.let {
        mapMemberRow(it)
    }

    fun getMemberById(id: String): Member? = dbQueries.selectMemberById(id).executeAsOneOrNull()?.let {
        mapMemberRow(it)
    }

    fun getAllMembersByDepartment(departmentId: String): List<Member> = dbQueries.selectAllMembersByDepartment(departmentId).executeAsList().map {
        mapMemberRow(it)
    }

    private fun mapMemberRow(it: com.example.firestationops.db.MemberEntity): Member =
        Member(
            id = it.id,
            departmentId = it.departmentId,
            email = it.email,
            firstName = it.firstName,
            lastName = it.lastName,
            memberNumber = it.memberNumber,
            roles = Json.decodeFromString(it.rolesJson),
            isActive = it.isActive.toInt() == 1,
            createdAt = it.createdAt,
            updatedAt = it.updatedAt
        )

    fun insertMember(member: Member) {
        upsertCanonicalMember(member)
    }

    fun upsertCanonicalMember(member: Member) {
        val normalized = com.example.firestationops.domain.membership.CalhounMembershipNormalizer.normalize(member)
        dbQueries.deleteMembersWithEmailExceptId(
            email = normalized.email,
            id = normalized.id
        )
        dbQueries.insertMember(
            id = normalized.id,
            departmentId = normalized.departmentId,
            email = normalized.email,
            firstName = normalized.firstName,
            lastName = normalized.lastName,
            memberNumber = normalized.memberNumber,
            rolesJson = Json.encodeToString(normalized.roles),
            isActive = if (normalized.isActive) 1 else 0,
            createdAt = normalized.createdAt,
            updatedAt = normalized.updatedAt
        )
    }

    fun deleteMemberById(id: String) {
        dbQueries.deleteMemberById(id)
    }

    // Session
    fun getSessionUserId(): String? = dbQueries.selectSession().executeAsOneOrNull()

    fun setSessionUserId(userId: String?) {
        if (userId == null) {
            dbQueries.clearSession()
        } else {
            dbQueries.insertSession(userId)
        }
    }

    // Attachments
    fun getAttachmentById(id: String): Attachment? = dbQueries.selectAttachmentById(id).executeAsOneOrNull()?.let {
        Attachment(
            id = it.id,
            departmentId = it.departmentId,
            localUri = it.localUri,
            remoteUrl = it.remoteUrl,
            syncStatus = SyncStatus.valueOf(it.syncStatus),
            createdAt = it.createdAt,
            createdByUserId = it.createdByUserId
        )
    }

    fun getAllAttachments(): List<Attachment> = dbQueries.selectAllAttachments().executeAsList().map {
        Attachment(
            id = it.id,
            departmentId = it.departmentId,
            localUri = it.localUri,
            remoteUrl = it.remoteUrl,
            syncStatus = SyncStatus.valueOf(it.syncStatus),
            createdAt = it.createdAt,
            createdByUserId = it.createdByUserId
        )
    }

    fun getAttachmentsByDepartment(departmentId: String): List<Attachment> = 
        dbQueries.selectAttachmentsByDepartment(departmentId).executeAsList().map {
            Attachment(
                id = it.id,
                departmentId = it.departmentId,
                localUri = it.localUri,
                remoteUrl = it.remoteUrl,
                syncStatus = SyncStatus.valueOf(it.syncStatus),
                createdAt = it.createdAt,
                createdByUserId = it.createdByUserId
            )
        }

    fun getPendingSyncAttachments(): List<Attachment> =
        dbQueries.selectPendingSyncAttachments().executeAsList().map {
            Attachment(
                id = it.id,
                departmentId = it.departmentId,
                localUri = it.localUri,
                remoteUrl = it.remoteUrl,
                syncStatus = SyncStatus.valueOf(it.syncStatus),
                createdAt = it.createdAt,
                createdByUserId = it.createdByUserId
            )
        }

    fun insertAttachment(attachment: Attachment) {
        dbQueries.insertAttachment(
            id = attachment.id,
            departmentId = attachment.departmentId,
            localUri = attachment.localUri,
            remoteUrl = attachment.remoteUrl,
            syncStatus = attachment.syncStatus.name,
            createdAt = attachment.createdAt,
            createdByUserId = attachment.createdByUserId
        )
    }

    fun updateAttachmentSyncStatus(id: String, syncStatus: SyncStatus) {
        dbQueries.updateAttachmentSyncStatus(syncStatus = syncStatus.name, id = id)
    }

    fun updateAttachmentRemoteUrl(id: String, remoteUrl: String) {
        dbQueries.updateAttachmentRemoteUrl(remoteUrl = remoteUrl, id = id)
    }

    fun deleteAttachment(id: String) {
        dbQueries.deleteAttachment(id)
    }

    // Incidents
    fun getAllIncidents(): List<Incident> =
        dbQueries.selectAllIncidents().executeAsList().map(::mapIncidentRow)

    fun getIncidentsByDepartment(departmentId: String): List<Incident> =
        dbQueries.selectIncidentsByDepartment(departmentId).executeAsList().map(::mapIncidentRow)

    fun getIncidentById(id: String): Incident? =
        dbQueries.selectIncidentById(id).executeAsOneOrNull()?.let(::mapIncidentRow)

    fun insertIncident(incident: Incident) {
        dbQueries.insertIncident(
            id = incident.id,
            departmentId = incident.departmentId,
            title = incident.title,
            summary = incident.summary,
            locationDescription = incident.locationDescription,
            incidentType = incident.incidentType.name,
            status = incident.status.name,
            createdAt = incident.createdAt,
            createdByUserId = incident.createdByUserId,
            updatedAt = incident.updatedAt,
            updatedByUserId = incident.updatedByUserId,
            closedAt = incident.closedAt,
            closedByUserId = incident.closedByUserId,
            syncStatus = incident.syncStatus.name
        )
    }

    private fun mapIncidentRow(row: com.example.firestationops.db.IncidentEntity): Incident =
        Incident(
            id = row.id,
            departmentId = row.departmentId,
            title = row.title,
            summary = row.summary,
            locationDescription = row.locationDescription,
            incidentType = IncidentType.valueOf(row.incidentType),
            status = IncidentStatus.valueOf(row.status),
            createdAt = row.createdAt,
            createdByUserId = row.createdByUserId,
            updatedAt = row.updatedAt,
            updatedByUserId = row.updatedByUserId,
            closedAt = row.closedAt,
            closedByUserId = row.closedByUserId,
            syncStatus = SyncStatus.valueOf(row.syncStatus)
        )

    fun getCommandLogEntriesByIncident(incidentId: String): List<CommandLogEntry> =
        dbQueries.selectCommandLogEntriesByIncident(incidentId).executeAsList().map(::mapCommandLogRow)

    fun insertCommandLogEntry(entry: CommandLogEntry) {
        dbQueries.insertCommandLogEntry(
            id = entry.id,
            incidentId = entry.incidentId,
            departmentId = entry.departmentId,
            message = entry.message,
            entryType = entry.entryType.name,
            createdAt = entry.createdAt,
            createdByUserId = entry.createdByUserId,
            incidentTimestamp = entry.incidentTimestamp,
            correctsEntryId = entry.correctsEntryId,
            syncStatus = entry.syncStatus.name
        )
    }

    private fun mapCommandLogRow(row: com.example.firestationops.db.CommandLogEntryEntity): CommandLogEntry =
        CommandLogEntry(
            id = row.id,
            incidentId = row.incidentId,
            departmentId = row.departmentId,
            message = row.message,
            entryType = CommandLogEntryType.valueOf(row.entryType),
            createdAt = row.createdAt,
            createdByUserId = row.createdByUserId,
            incidentTimestamp = row.incidentTimestamp,
            correctsEntryId = row.correctsEntryId,
            syncStatus = SyncStatus.valueOf(row.syncStatus)
        )

    fun getUnitAssignmentsByIncident(incidentId: String): List<IncidentUnitAssignment> =
        dbQueries.selectUnitAssignmentsByIncident(incidentId).executeAsList().map(::mapUnitAssignmentRow)

    fun insertUnitAssignment(assignment: IncidentUnitAssignment) {
        dbQueries.insertUnitAssignment(
            id = assignment.id,
            incidentId = assignment.incidentId,
            departmentId = assignment.departmentId,
            apparatusId = assignment.apparatusId,
            status = assignment.status.name,
            assignedAt = assignment.assignedAt,
            assignedByUserId = assignment.assignedByUserId,
            updatedAt = assignment.updatedAt,
            updatedByUserId = assignment.updatedByUserId,
            syncStatus = assignment.syncStatus.name
        )
    }

    private fun mapUnitAssignmentRow(row: com.example.firestationops.db.IncidentUnitAssignmentEntity): IncidentUnitAssignment =
        IncidentUnitAssignment(
            id = row.id,
            incidentId = row.incidentId,
            departmentId = row.departmentId,
            apparatusId = row.apparatusId,
            status = AssignmentStatus.valueOf(row.status),
            assignedAt = row.assignedAt,
            assignedByUserId = row.assignedByUserId,
            updatedAt = row.updatedAt,
            updatedByUserId = row.updatedByUserId,
            syncStatus = SyncStatus.valueOf(row.syncStatus)
        )

    fun getPersonnelAssignmentsByIncident(incidentId: String): List<PersonnelAssignment> =
        dbQueries.selectPersonnelAssignmentsByIncident(incidentId).executeAsList().map(::mapPersonnelAssignmentRow)

    fun insertPersonnelAssignment(assignment: PersonnelAssignment) {
        dbQueries.insertPersonnelAssignment(
            id = assignment.id,
            incidentId = assignment.incidentId,
            departmentId = assignment.departmentId,
            memberId = assignment.memberId,
            status = assignment.status.name,
            assignedAt = assignment.assignedAt,
            assignedByUserId = assignment.assignedByUserId,
            updatedAt = assignment.updatedAt,
            updatedByUserId = assignment.updatedByUserId,
            syncStatus = assignment.syncStatus.name
        )
    }

    private fun mapPersonnelAssignmentRow(row: com.example.firestationops.db.PersonnelAssignmentEntity): PersonnelAssignment =
        PersonnelAssignment(
            id = row.id,
            incidentId = row.incidentId,
            departmentId = row.departmentId,
            memberId = row.memberId,
            status = AssignmentStatus.valueOf(row.status),
            assignedAt = row.assignedAt,
            assignedByUserId = row.assignedByUserId,
            updatedAt = row.updatedAt,
            updatedByUserId = row.updatedByUserId,
            syncStatus = SyncStatus.valueOf(row.syncStatus)
        )

    fun getPendingSyncIncidents(): List<Incident> =
        dbQueries.selectPendingSyncIncidents().executeAsList().map(::mapIncidentRow)

    fun getPendingSyncCommandLogEntries(): List<CommandLogEntry> =
        dbQueries.selectPendingSyncCommandLogEntries().executeAsList().map(::mapCommandLogRow)

    fun getPendingSyncUnitAssignments(): List<IncidentUnitAssignment> =
        dbQueries.selectPendingSyncUnitAssignments().executeAsList().map(::mapUnitAssignmentRow)

    fun getPendingSyncPersonnelAssignments(): List<PersonnelAssignment> =
        dbQueries.selectPendingSyncPersonnelAssignments().executeAsList().map(::mapPersonnelAssignmentRow)

    fun updateIncidentSyncStatus(id: String, syncStatus: SyncStatus) {
        dbQueries.updateIncidentSyncStatus(syncStatus = syncStatus.name, id = id)
    }

    fun updateCommandLogEntrySyncStatus(id: String, syncStatus: SyncStatus) {
        dbQueries.updateCommandLogEntrySyncStatus(syncStatus = syncStatus.name, id = id)
    }

    fun updateUnitAssignmentSyncStatus(id: String, syncStatus: SyncStatus) {
        dbQueries.updateUnitAssignmentSyncStatus(syncStatus = syncStatus.name, id = id)
    }

    fun updatePersonnelAssignmentSyncStatus(id: String, syncStatus: SyncStatus) {
        dbQueries.updatePersonnelAssignmentSyncStatus(syncStatus = syncStatus.name, id = id)
    }
}
