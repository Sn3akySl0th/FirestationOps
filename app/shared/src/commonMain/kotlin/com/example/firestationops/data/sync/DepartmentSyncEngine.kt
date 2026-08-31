package com.example.firestationops.data.sync

import com.example.firestationops.data.firebase.FirestoreMappers
import com.example.firestationops.data.firebase.FirestorePaths
import com.example.firestationops.domain.model.Attachment
import com.example.firestationops.domain.model.SyncStatus
import com.example.firestationops.domain.repository.AttachmentRepository
import com.example.firestationops.domain.repository.CatalogRepository
import com.example.firestationops.domain.repository.DeficiencyRepository
import com.example.firestationops.domain.repository.IncidentRepository
import com.example.firestationops.domain.repository.InspectionRepository
import com.example.firestationops.domain.sync.SyncActivityAction
import com.example.firestationops.domain.sync.SyncActivityDirection
import com.example.firestationops.domain.sync.SyncActivityItem
import com.example.firestationops.domain.sync.SyncActivityRecordType
import com.example.firestationops.domain.sync.SyncRecordDiffer
import com.example.firestationops.domain.sync.SyncResult

class DepartmentSyncEngine(
    private val cloudSyncClient: CloudSyncClient,
    private val attachmentCache: SyncAttachmentCache,
    private val catalogRepository: CatalogRepository,
    private val attachmentRepository: AttachmentRepository,
    private val inspectionRepository: InspectionRepository,
    private val deficiencyRepository: DeficiencyRepository,
    private val incidentRepository: IncidentRepository
) {
    suspend fun syncDepartment(departmentId: String): SyncResult {
        val downloadedItems = mutableListOf<SyncActivityItem>()
        val uploadedItems = mutableListOf<SyncActivityItem>()
        var failedCount = 0
        val errors = mutableListOf<String>()
        val collector = SyncActivityCollector(downloadedItems, uploadedItems)

        suspend fun <T> runStep(label: String, block: suspend () -> T): T? {
            return try {
                block()
            } catch (error: Exception) {
                failedCount++
                errors += "$label: ${error.message ?: "Unknown error"}"
                null
            }
        }

        runStep("Download department catalog") {
            pullDepartmentCatalog(departmentId, collector)
            catalogRepository.notifyCatalogUpdated()
        }
        runStep("Download inspections") {
            pullInspections(departmentId, collector)
        }
        runStep("Download deficiencies") {
            pullDeficiencies(departmentId, collector)
        }
        runStep("Download incidents") {
            pullIncidents(departmentId, collector)
        }
        runStep("Download attachments") {
            pullAttachments(departmentId, collector)
        }

        val pendingAttachments = attachmentRepository.getPendingSyncAttachments()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId }

        pendingAttachments.forEach { attachment ->
            runStep("Attachment ${attachment.id}") {
                uploadAttachment(attachment)
                collector.recordUpload(
                    recordType = SyncActivityRecordType.ATTACHMENT,
                    recordId = attachment.id,
                    title = "Photo attachment",
                    detail = attachment.localUri?.substringAfterLast('/')
                )
            }
        }

        val pendingInspections = inspectionRepository.getPendingSyncInspections()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId && it.isFinalized }

        pendingInspections.forEach { inspection ->
            runStep("Inspection ${inspection.id}") {
                cloudSyncClient.setDocument(
                    FirestorePaths.inspection(departmentId, inspection.id),
                    FirestoreMappers.inspectionToMap(inspection)
                )
                inspectionRepository.updateSyncStatus(inspection.id, SyncStatus.SYNCED)
                collector.recordUpload(
                    recordType = SyncActivityRecordType.INSPECTION,
                    recordId = inspection.id,
                    title = inspectionLabel(inspection),
                    detail = "Submitted inspection"
                )
            }
        }

        val pendingDeficiencies = deficiencyRepository.getPendingSyncDeficiencies()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId }

        pendingDeficiencies.forEach { deficiency ->
            runStep("Deficiency ${deficiency.id}") {
                cloudSyncClient.setDocument(
                    FirestorePaths.deficiency(departmentId, deficiency.id),
                    FirestoreMappers.deficiencyToMap(deficiency)
                )
                deficiencyRepository.updateSyncStatus(deficiency.id, SyncStatus.SYNCED)
                collector.recordUpload(
                    recordType = SyncActivityRecordType.DEFICIENCY,
                    recordId = deficiency.id,
                    title = deficiency.title,
                    detail = deficiency.severity.name.replace('_', ' ').lowercase()
                )
            }
        }

        val pendingIncidents = incidentRepository.getPendingSyncIncidents()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId }

        pendingIncidents.forEach { incident ->
            runStep("Incident ${incident.id}") {
                cloudSyncClient.setDocument(
                    FirestorePaths.incident(departmentId, incident.id),
                    FirestoreMappers.incidentToMap(incident)
                )
                incidentRepository.updateIncidentSyncStatus(incident.id, SyncStatus.SYNCED)
                collector.recordUpload(
                    recordType = SyncActivityRecordType.INCIDENT,
                    recordId = incident.id,
                    title = incident.title.ifBlank { "Incident report" },
                    detail = incident.status.name.replace('_', ' ').lowercase()
                )
            }
        }

        incidentRepository.getPendingSyncCommandLogEntries()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId }
            .forEach { entry ->
                runStep("Command log ${entry.id}") {
                    cloudSyncClient.setDocument(
                        FirestorePaths.commandLogEntry(departmentId, entry.incidentId, entry.id),
                        FirestoreMappers.commandLogEntryToMap(entry)
                    )
                    incidentRepository.updateCommandLogEntrySyncStatus(entry.id, SyncStatus.SYNCED)
                    collector.recordUpload(
                        recordType = SyncActivityRecordType.COMMAND_LOG,
                        recordId = entry.id,
                        title = entry.message.take(48),
                        detail = "Command log entry"
                    )
                }
            }

        incidentRepository.getPendingSyncUnitAssignments()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId }
            .forEach { assignment ->
                runStep("Unit assignment ${assignment.id}") {
                    cloudSyncClient.setDocument(
                        FirestorePaths.unitAssignment(departmentId, assignment.incidentId, assignment.id),
                        FirestoreMappers.unitAssignmentToMap(assignment)
                    )
                    incidentRepository.updateUnitAssignmentSyncStatus(assignment.id, SyncStatus.SYNCED)
                    collector.recordUpload(
                        recordType = SyncActivityRecordType.UNIT_ASSIGNMENT,
                        recordId = assignment.id,
                        title = "Unit assignment",
                        detail = assignment.status.name.replace('_', ' ').lowercase()
                    )
                }
            }

        incidentRepository.getPendingSyncPersonnelAssignments()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId }
            .forEach { assignment ->
                runStep("Personnel assignment ${assignment.id}") {
                    cloudSyncClient.setDocument(
                        FirestorePaths.personnelAssignment(departmentId, assignment.incidentId, assignment.id),
                        FirestoreMappers.personnelAssignmentToMap(assignment)
                    )
                    incidentRepository.updatePersonnelAssignmentSyncStatus(assignment.id, SyncStatus.SYNCED)
                    collector.recordUpload(
                        recordType = SyncActivityRecordType.PERSONNEL_ASSIGNMENT,
                        recordId = assignment.id,
                        title = "Personnel assignment",
                        detail = assignment.status.name.replace('_', ' ').lowercase()
                    )
                }
            }

        return SyncResult(
            uploadedItems = uploadedItems,
            downloadedItems = downloadedItems,
            failedCount = failedCount,
            errors = errors
        )
    }

    private suspend fun pullDepartmentCatalog(departmentId: String, collector: SyncActivityCollector) {
        val departmentSnapshot = cloudSyncClient.getDocument(FirestorePaths.department(departmentId))
        if (departmentSnapshot.exists) {
            val department = FirestoreMappers.departmentFromMap(departmentId, departmentSnapshot.data)
            if (department != null) {
                collector.applyDownload(
                    remote = department,
                    local = catalogRepository.findDepartment(departmentId),
                    matches = SyncRecordDiffer::departmentsMatch,
                    recordType = SyncActivityRecordType.DEPARTMENT,
                    recordId = department.id,
                    title = department.name,
                    detail = "Department profile"
                ) {
                    catalogRepository.applyDepartment(department)
                }
            }
        }

        cloudSyncClient.listCollection("${FirestorePaths.department(departmentId)}/stations").forEach { document ->
            val station = FirestoreMappers.stationFromMap(document.id, document.data) ?: return@forEach
            collector.applyDownload(
                remote = station,
                local = catalogRepository.findStation(station.id),
                matches = SyncRecordDiffer::stationsMatch,
                recordType = SyncActivityRecordType.STATION,
                recordId = station.id,
                title = station.name,
                detail = station.address
            ) {
                catalogRepository.applyStation(station)
            }
        }

        cloudSyncClient.listCollection("${FirestorePaths.department(departmentId)}/apparatus").forEach { document ->
            val apparatus = FirestoreMappers.apparatusFromMap(document.id, document.data) ?: return@forEach
            collector.applyDownload(
                remote = apparatus,
                local = catalogRepository.findApparatus(apparatus.id),
                matches = SyncRecordDiffer::apparatusMatch,
                recordType = SyncActivityRecordType.APPARATUS,
                recordId = apparatus.id,
                title = apparatus.name,
                detail = apparatus.radioName
            ) {
                catalogRepository.applyApparatus(apparatus)
            }
        }

        cloudSyncClient.listCollection("${FirestorePaths.department(departmentId)}/templates").forEach { document ->
            val template = FirestoreMappers.templateFromMap(document.id, document.data) ?: return@forEach
            collector.applyDownload(
                remote = template,
                local = catalogRepository.findTemplate(template.id),
                matches = SyncRecordDiffer::templatesMatch,
                recordType = SyncActivityRecordType.TEMPLATE,
                recordId = template.id,
                title = template.name,
                detail = template.apparatusType
            ) {
                catalogRepository.applyTemplate(template)
            }
        }

        cloudSyncClient.listCollection("${FirestorePaths.department(departmentId)}/members").forEach { document ->
            val member = FirestoreMappers.memberFromMap(document.id, document.data) ?: return@forEach
            collector.applyDownload(
                remote = member,
                local = catalogRepository.findMember(member.id),
                matches = SyncRecordDiffer::membersMatch,
                recordType = SyncActivityRecordType.MEMBER,
                recordId = member.id,
                title = "${member.firstName} ${member.lastName}",
                detail = member.memberNumber?.let { "Member #$it" } ?: member.email
            ) {
                catalogRepository.applyMember(member)
            }
        }
    }

    private suspend fun pullInspections(departmentId: String, collector: SyncActivityCollector) {
        val pendingLocalIds = inspectionRepository.getPendingSyncInspections()
            .getOrElse { emptyList() }
            .map { it.id }
            .toSet()

        cloudSyncClient.listCollection("${FirestorePaths.department(departmentId)}/inspections").forEach { document ->
            val inspection = FirestoreMappers.inspectionFromMap(document.id, document.data) ?: return@forEach
            if (inspection.id in pendingLocalIds) return@forEach

            collector.applyDownload(
                remote = inspection,
                local = inspectionRepository.getInspection(inspection.id).getOrNull(),
                matches = SyncRecordDiffer::inspectionsMatch,
                recordType = SyncActivityRecordType.INSPECTION,
                recordId = inspection.id,
                title = inspectionLabel(inspection),
                detail = if (inspection.isFinalized) "Submitted inspection" else "Draft inspection"
            ) {
                inspectionRepository.saveInspection(inspection)
            }
        }
    }

    private suspend fun pullDeficiencies(departmentId: String, collector: SyncActivityCollector) {
        val pendingLocalIds = deficiencyRepository.getPendingSyncDeficiencies()
            .getOrElse { emptyList() }
            .map { it.id }
            .toSet()

        cloudSyncClient.listCollection("${FirestorePaths.department(departmentId)}/deficiencies").forEach { document ->
            val deficiency = FirestoreMappers.deficiencyFromMap(document.id, document.data) ?: return@forEach
            if (deficiency.id in pendingLocalIds) return@forEach

            collector.applyDownload(
                remote = deficiency,
                local = deficiencyRepository.getDeficiency(deficiency.id).getOrNull(),
                matches = SyncRecordDiffer::deficienciesMatch,
                recordType = SyncActivityRecordType.DEFICIENCY,
                recordId = deficiency.id,
                title = deficiency.title,
                detail = deficiency.status.name.replace('_', ' ').lowercase()
            ) {
                deficiencyRepository.saveDeficiency(deficiency)
            }
        }
    }

    private suspend fun pullIncidents(departmentId: String, collector: SyncActivityCollector) {
        val pendingIncidentIds = incidentRepository.getPendingSyncIncidents()
            .getOrElse { emptyList() }
            .map { it.id }
            .toSet()
        val pendingCommandLogIds = incidentRepository.getPendingSyncCommandLogEntries()
            .getOrElse { emptyList() }
            .map { it.id }
            .toSet()
        val pendingUnitAssignmentIds = incidentRepository.getPendingSyncUnitAssignments()
            .getOrElse { emptyList() }
            .map { it.id }
            .toSet()
        val pendingPersonnelAssignmentIds = incidentRepository.getPendingSyncPersonnelAssignments()
            .getOrElse { emptyList() }
            .map { it.id }
            .toSet()

        cloudSyncClient.listCollection("${FirestorePaths.department(departmentId)}/incidents").forEach { document ->
            val incident = FirestoreMappers.incidentFromMap(document.id, document.data) ?: return@forEach

            if (incident.id !in pendingIncidentIds) {
                collector.applyDownload(
                    remote = incident,
                    local = incidentRepository.getIncident(incident.id).getOrNull(),
                    matches = SyncRecordDiffer::incidentsMatch,
                    recordType = SyncActivityRecordType.INCIDENT,
                    recordId = incident.id,
                    title = incident.title.ifBlank { "Incident report" },
                    detail = incident.status.name.replace('_', ' ').lowercase()
                ) {
                    incidentRepository.saveIncident(incident)
                }
            }

            val incidentBasePath = FirestorePaths.incident(departmentId, incident.id)

            cloudSyncClient.listCollection("$incidentBasePath/commandLog").forEach { entryDocument ->
                val entry = FirestoreMappers.commandLogEntryFromMap(entryDocument.id, entryDocument.data) ?: return@forEach
                if (entry.id in pendingCommandLogIds) return@forEach

                collector.applyDownload(
                    remote = entry,
                    local = incidentRepository.findCommandLogEntry(entry.id),
                    matches = SyncRecordDiffer::commandLogEntriesMatch,
                    recordType = SyncActivityRecordType.COMMAND_LOG,
                    recordId = entry.id,
                    title = entry.message.take(48),
                    detail = "Command log entry"
                ) {
                    incidentRepository.appendCommandLogEntry(entry)
                }
            }

            cloudSyncClient.listCollection("$incidentBasePath/unitAssignments").forEach { assignmentDocument ->
                val assignment = FirestoreMappers.unitAssignmentFromMap(assignmentDocument.id, assignmentDocument.data)
                    ?: return@forEach
                if (assignment.id in pendingUnitAssignmentIds) return@forEach

                collector.applyDownload(
                    remote = assignment,
                    local = incidentRepository.findUnitAssignment(assignment.id),
                    matches = SyncRecordDiffer::unitAssignmentsMatch,
                    recordType = SyncActivityRecordType.UNIT_ASSIGNMENT,
                    recordId = assignment.id,
                    title = "Unit assignment",
                    detail = assignment.status.name.replace('_', ' ').lowercase()
                ) {
                    incidentRepository.saveUnitAssignment(assignment)
                }
            }

            cloudSyncClient.listCollection("$incidentBasePath/personnelAssignments").forEach { assignmentDocument ->
                val assignment = FirestoreMappers.personnelAssignmentFromMap(
                    assignmentDocument.id,
                    assignmentDocument.data
                ) ?: return@forEach
                if (assignment.id in pendingPersonnelAssignmentIds) return@forEach

                collector.applyDownload(
                    remote = assignment,
                    local = incidentRepository.findPersonnelAssignment(assignment.id),
                    matches = SyncRecordDiffer::personnelAssignmentsMatch,
                    recordType = SyncActivityRecordType.PERSONNEL_ASSIGNMENT,
                    recordId = assignment.id,
                    title = "Personnel assignment",
                    detail = assignment.status.name.replace('_', ' ').lowercase()
                ) {
                    incidentRepository.savePersonnelAssignment(assignment)
                }
            }
        }
    }

    private suspend fun pullAttachments(departmentId: String, collector: SyncActivityCollector) {
        val pendingLocalIds = attachmentRepository.getPendingSyncAttachments()
            .getOrElse { emptyList() }
            .map { it.id }
            .toSet()

        cloudSyncClient.listCollection("${FirestorePaths.department(departmentId)}/attachments").forEach { document ->
            val remoteAttachment = FirestoreMappers.attachmentFromMap(document.id, document.data) ?: return@forEach
            if (remoteAttachment.id in pendingLocalIds) return@forEach

            val existing = attachmentRepository.getAttachment(remoteAttachment.id).getOrNull()
            val existingLocalPath = existing?.localUri
            if (!existingLocalPath.isNullOrBlank() &&
                attachmentCache.fileExists(existingLocalPath) &&
                existing != null &&
                SyncRecordDiffer.attachmentsMatch(remoteAttachment, existing)
            ) {
                return@forEach
            }

            if (!existingLocalPath.isNullOrBlank() && attachmentCache.fileExists(existingLocalPath)) {
                if (existing != null && SyncRecordDiffer.attachmentsMatch(remoteAttachment, existing)) {
                    return@forEach
                }
                attachmentRepository.saveAttachment(
                    remoteAttachment.copy(localUri = existingLocalPath, syncStatus = SyncStatus.SYNCED)
                )
                if (existing != null && !SyncRecordDiffer.attachmentsMatch(remoteAttachment, existing)) {
                    collector.recordDownload(
                        recordType = SyncActivityRecordType.ATTACHMENT,
                        recordId = remoteAttachment.id,
                        title = "Photo attachment",
                        detail = existingLocalPath.substringAfterLast('/'),
                        action = SyncActivityAction.UPDATED
                    )
                }
                return@forEach
            }

            val localFilePath = attachmentCache.attachmentFilePath(remoteAttachment.id)
            val storagePath = FirestorePaths.attachmentStorage(departmentId, remoteAttachment.id)
            cloudSyncClient.downloadStorageFile(storagePath, localFilePath)

            attachmentRepository.saveAttachment(
                remoteAttachment.copy(
                    localUri = localFilePath,
                    syncStatus = SyncStatus.SYNCED
                )
            )
            collector.recordDownload(
                recordType = SyncActivityRecordType.ATTACHMENT,
                recordId = remoteAttachment.id,
                title = "Photo attachment",
                detail = localFilePath.substringAfterLast('/'),
                action = if (existing == null) SyncActivityAction.NEW else SyncActivityAction.UPDATED
            )
        }
    }

    private suspend fun uploadAttachment(attachment: Attachment) {
        val localPath = attachment.localUri
            ?: error("Attachment ${attachment.id} has no local file path.")

        if (!attachmentCache.fileExists(localPath)) {
            attachmentRepository.updateSyncStatus(attachment.id, SyncStatus.SYNC_FAILED)
            error("Attachment file not found at $localPath")
        }

        val storagePath = FirestorePaths.attachmentStorage(attachment.departmentId, attachment.id)
        val downloadUrl = cloudSyncClient.uploadStorageFile(storagePath, localPath)

        cloudSyncClient.setDocument(
            FirestorePaths.attachment(attachment.departmentId, attachment.id),
            FirestoreMappers.attachmentToMap(
                attachment.copy(remoteUrl = downloadUrl, syncStatus = SyncStatus.SYNCED)
            )
        )

        attachmentRepository.updateRemoteUrl(attachment.id, downloadUrl)
    }

    private suspend fun inspectionLabel(inspection: com.example.firestationops.domain.model.Inspection): String {
        val apparatus = catalogRepository.findApparatus(inspection.apparatusId)
        val template = catalogRepository.findTemplate(inspection.templateId)
        val apparatusLabel = apparatus?.radioName ?: inspection.apparatusId
        val templateLabel = template?.name ?: "Inspection"
        return "$apparatusLabel · $templateLabel"
    }

    private class SyncActivityCollector(
        private val downloadedItems: MutableList<SyncActivityItem>,
        private val uploadedItems: MutableList<SyncActivityItem>
    ) {
        suspend fun <T> applyDownload(
            remote: T,
            local: T?,
            matches: (T, T) -> Boolean,
            recordType: SyncActivityRecordType,
            recordId: String,
            title: String,
            detail: String?,
            apply: suspend () -> Unit
        ) {
            when {
                local == null -> {
                    apply()
                    recordDownload(recordType, recordId, title, detail, SyncActivityAction.NEW)
                }
                !matches(remote, local) -> {
                    apply()
                    recordDownload(recordType, recordId, title, detail, SyncActivityAction.UPDATED)
                }
            }
        }

        fun recordDownload(
            recordType: SyncActivityRecordType,
            recordId: String,
            title: String,
            detail: String?,
            action: SyncActivityAction
        ) {
            downloadedItems += SyncActivityItem(
                direction = SyncActivityDirection.DOWNLOAD,
                recordType = recordType,
                recordId = recordId,
                title = title,
                detail = detail,
                action = action
            )
        }

        fun recordUpload(
            recordType: SyncActivityRecordType,
            recordId: String,
            title: String,
            detail: String?
        ) {
            uploadedItems += SyncActivityItem(
                direction = SyncActivityDirection.UPLOAD,
                recordType = recordType,
                recordId = recordId,
                title = title,
                detail = detail,
                action = SyncActivityAction.UPDATED
            )
        }
    }
}
