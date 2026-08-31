package com.example.firestationops.data.sync

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.data.firebase.FirestoreMappers
import com.example.firestationops.data.firebase.FirestorePaths
import com.example.firestationops.domain.model.Attachment
import com.example.firestationops.domain.sync.AttachmentUploadProgressTracker
import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.model.SyncStatus
import com.example.firestationops.domain.repository.AttachmentRepository
import com.example.firestationops.domain.repository.CatalogRepository
import com.example.firestationops.domain.repository.DeficiencyRepository
import com.example.firestationops.domain.repository.IncidentRepository
import com.example.firestationops.domain.repository.InspectionRepository
import com.example.firestationops.domain.repository.SyncConflictRepository
import com.example.firestationops.domain.sync.SyncActivityAction
import com.example.firestationops.domain.sync.SyncActivityDirection
import com.example.firestationops.domain.sync.SyncActivityItem
import com.example.firestationops.domain.sync.SyncActivityRecordType
import com.example.firestationops.domain.sync.SyncConflict
import com.example.firestationops.domain.sync.InspectionDuplicateDetector
import com.example.firestationops.domain.InspectionComplianceCalculator
import com.example.firestationops.domain.sync.SyncConflictDetector
import com.example.firestationops.domain.sync.SyncConflictRecordType
import com.example.firestationops.domain.sync.SyncRecordDiffer
import com.example.firestationops.domain.sync.SyncRecordSnapshot
import com.example.firestationops.domain.sync.SyncResult

class DepartmentSyncEngine(
    private val cloudSyncClient: CloudSyncClient,
    private val attachmentCache: SyncAttachmentCache,
    private val catalogRepository: CatalogRepository,
    private val attachmentRepository: AttachmentRepository,
    private val inspectionRepository: InspectionRepository,
    private val deficiencyRepository: DeficiencyRepository,
    private val incidentRepository: IncidentRepository,
    private val syncConflictRepository: SyncConflictRepository
) {
    suspend fun syncDepartment(departmentId: String): SyncResult {
        val downloadedItems = mutableListOf<SyncActivityItem>()
        val uploadedItems = mutableListOf<SyncActivityItem>()
        var failedCount = 0
        var conflictCount = 0
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

        pendingInspections
            .sortedWith(compareBy<com.example.firestationops.domain.model.Inspection> { it.voidedAt == null })
            .forEach { inspection ->
            runStep("Inspection ${inspection.id}") {
                if (uploadInspection(departmentId, inspection, collector)) {
                    conflictCount++
                }
            }
        }

        val pendingDeficiencies = deficiencyRepository.getPendingSyncDeficiencies()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId }

        pendingDeficiencies.forEach { deficiency ->
            runStep("Deficiency ${deficiency.id}") {
                if (uploadDeficiency(departmentId, deficiency, collector)) {
                    conflictCount++
                }
            }
        }

        val pendingIncidents = incidentRepository.getPendingSyncIncidents()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId }

        pendingIncidents.forEach { incident ->
            runStep("Incident ${incident.id}") {
                if (uploadIncident(departmentId, incident, collector)) {
                    conflictCount++
                }
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
            conflictCount = conflictCount,
            errors = errors
        )
    }

    private suspend fun uploadInspection(
        departmentId: String,
        inspection: com.example.firestationops.domain.model.Inspection,
        collector: SyncActivityCollector
    ): Boolean {
        val remoteInspections = loadRemoteInspections(departmentId)
        val template = catalogRepository.findTemplate(inspection.templateId)
        val frequencyHours = template?.frequencyHours ?: InspectionComplianceCalculator.DEFAULT_FREQUENCY_HOURS
        val duplicate = InspectionDuplicateDetector.findDuplicate(
            local = inspection,
            remoteInspections = remoteInspections,
            frequencyHours = frequencyHours
        )
        if (duplicate != null) {
            recordInspectionConflict(departmentId, inspection, duplicate)
            return true
        }

        cloudSyncClient.setDocument(
            FirestorePaths.inspection(departmentId, inspection.id),
            FirestoreMappers.inspectionToMap(inspection)
        )
        inspectionRepository.updateSyncStatus(inspection.id, SyncStatus.SYNCED)
        collector.recordUpload(
            recordType = SyncActivityRecordType.INSPECTION,
            recordId = inspection.id,
            title = inspectionLabel(inspection),
            detail = if (inspection.voidedAt != null) "Voided duplicate inspection" else "Submitted inspection"
        )
        return false
    }

    private suspend fun loadRemoteInspections(departmentId: String): List<com.example.firestationops.domain.model.Inspection> =
        cloudSyncClient.listCollection("${FirestorePaths.department(departmentId)}/inspections")
            .mapNotNull { document ->
                FirestoreMappers.inspectionFromMap(document.id, document.data)
            }

    private suspend fun recordInspectionConflict(
        departmentId: String,
        local: com.example.firestationops.domain.model.Inspection,
        remote: com.example.firestationops.domain.model.Inspection
    ) {
        syncConflictRepository.saveConflict(
            SyncConflict(
                id = "conflict-${local.id}",
                departmentId = departmentId,
                recordType = SyncConflictRecordType.INSPECTION,
                recordId = local.id,
                localSnapshotJson = SyncRecordSnapshot.encodeInspection(local),
                remoteSnapshotJson = SyncRecordSnapshot.encodeInspection(remote),
                detectedAt = currentTimeMillis()
            )
        )
        inspectionRepository.updateSyncStatus(local.id, SyncStatus.CONFLICT)
    }

    private suspend fun uploadDeficiency(
        departmentId: String,
        deficiency: Deficiency,
        collector: SyncActivityCollector
    ): Boolean {
        val remote = loadRemoteDeficiency(departmentId, deficiency.id)
        val baseline = loadDeficiencyBaseline(deficiency.id)
        if (SyncConflictDetector.isDeficiencyUploadConflict(deficiency, remote, baseline)) {
            recordDeficiencyConflict(departmentId, deficiency, requireNotNull(remote))
            return true
        }

        cloudSyncClient.setDocument(
            FirestorePaths.deficiency(departmentId, deficiency.id),
            FirestoreMappers.deficiencyToMap(deficiency)
        )
        deficiencyRepository.updateSyncStatus(deficiency.id, SyncStatus.SYNCED)
        saveDeficiencyBaseline(deficiency)
        collector.recordUpload(
            recordType = SyncActivityRecordType.DEFICIENCY,
            recordId = deficiency.id,
            title = deficiency.title,
            detail = deficiency.severity.name.replace('_', ' ').lowercase()
        )
        return false
    }

    private suspend fun uploadIncident(
        departmentId: String,
        incident: Incident,
        collector: SyncActivityCollector
    ): Boolean {
        val remote = loadRemoteIncident(departmentId, incident.id)
        val baseline = loadIncidentBaseline(incident.id)
        if (SyncConflictDetector.isIncidentUploadConflict(incident, remote, baseline)) {
            recordIncidentConflict(departmentId, incident, requireNotNull(remote))
            return true
        }

        cloudSyncClient.setDocument(
            FirestorePaths.incident(departmentId, incident.id),
            FirestoreMappers.incidentToMap(incident)
        )
        incidentRepository.updateIncidentSyncStatus(incident.id, SyncStatus.SYNCED)
        saveIncidentBaseline(incident)
        collector.recordUpload(
            recordType = SyncActivityRecordType.INCIDENT,
            recordId = incident.id,
            title = incident.title.ifBlank { "Incident report" },
            detail = incident.status.name.replace('_', ' ').lowercase()
        )
        return false
    }

    private suspend fun loadRemoteDeficiency(departmentId: String, deficiencyId: String): Deficiency? {
        val document = cloudSyncClient.getDocument(FirestorePaths.deficiency(departmentId, deficiencyId))
        if (!document.exists) return null
        return FirestoreMappers.deficiencyFromMap(document.id, document.data)
    }

    private suspend fun loadRemoteIncident(departmentId: String, incidentId: String): Incident? {
        val document = cloudSyncClient.getDocument(FirestorePaths.incident(departmentId, incidentId))
        if (!document.exists) return null
        return FirestoreMappers.incidentFromMap(document.id, document.data)
    }

    private suspend fun loadDeficiencyBaseline(deficiencyId: String): Deficiency? =
        syncConflictRepository
            .getBaselineSnapshot(SyncConflictRecordType.DEFICIENCY, deficiencyId)
            ?.let(SyncRecordSnapshot::decodeDeficiency)

    private suspend fun loadIncidentBaseline(incidentId: String): Incident? =
        syncConflictRepository
            .getBaselineSnapshot(SyncConflictRecordType.INCIDENT, incidentId)
            ?.let(SyncRecordSnapshot::decodeIncident)

    private suspend fun saveDeficiencyBaseline(deficiency: Deficiency) {
        syncConflictRepository.saveBaseline(
            recordType = SyncConflictRecordType.DEFICIENCY,
            recordId = deficiency.id,
            snapshotJson = SyncRecordSnapshot.encodeDeficiency(deficiency)
        )
    }

    private suspend fun saveIncidentBaseline(incident: Incident) {
        syncConflictRepository.saveBaseline(
            recordType = SyncConflictRecordType.INCIDENT,
            recordId = incident.id,
            snapshotJson = SyncRecordSnapshot.encodeIncident(incident)
        )
    }

    private suspend fun recordDeficiencyConflict(
        departmentId: String,
        local: Deficiency,
        remote: Deficiency
    ) {
        syncConflictRepository.saveConflict(
            SyncConflict(
                id = "conflict-${local.id}",
                departmentId = departmentId,
                recordType = SyncConflictRecordType.DEFICIENCY,
                recordId = local.id,
                localSnapshotJson = SyncRecordSnapshot.encodeDeficiency(local),
                remoteSnapshotJson = SyncRecordSnapshot.encodeDeficiency(remote),
                detectedAt = currentTimeMillis()
            )
        )
        deficiencyRepository.updateSyncStatus(local.id, SyncStatus.CONFLICT)
    }

    private suspend fun recordIncidentConflict(
        departmentId: String,
        local: Incident,
        remote: Incident
    ) {
        syncConflictRepository.saveConflict(
            SyncConflict(
                id = "conflict-${local.id}",
                departmentId = departmentId,
                recordType = SyncConflictRecordType.INCIDENT,
                recordId = local.id,
                localSnapshotJson = SyncRecordSnapshot.encodeIncident(local),
                remoteSnapshotJson = SyncRecordSnapshot.encodeIncident(remote),
                detectedAt = currentTimeMillis()
            )
        )
        incidentRepository.updateIncidentSyncStatus(local.id, SyncStatus.CONFLICT)
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
                saveDeficiencyBaseline(deficiency)
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
                    saveIncidentBaseline(incident)
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
            attachmentRepository.markUploadFailed(
                id = attachment.id,
                error = "Photo file is missing on this device.",
                failedAt = currentTimeMillis()
            )
            error("Attachment file not found at $localPath")
        }

        val storagePath = FirestorePaths.attachmentStorage(attachment.departmentId, attachment.id)
        try {
            AttachmentUploadProgressTracker.reportProgress(attachment.id, 0)
            val downloadUrl = cloudSyncClient.uploadStorageFile(
                storagePath = storagePath,
                localFilePath = localPath,
                onProgress = { progress ->
                    AttachmentUploadProgressTracker.reportProgress(attachment.id, progress)
                }
            )

            cloudSyncClient.setDocument(
                FirestorePaths.attachment(attachment.departmentId, attachment.id),
                FirestoreMappers.attachmentToMap(
                    attachment.copy(remoteUrl = downloadUrl, syncStatus = SyncStatus.SYNCED)
                )
            )

            attachmentRepository.updateRemoteUrl(attachment.id, downloadUrl)
        } catch (error: Exception) {
            attachmentRepository.markUploadFailed(
                id = attachment.id,
                error = error.message ?: "Photo upload failed.",
                failedAt = currentTimeMillis()
            )
            throw error
        } finally {
            AttachmentUploadProgressTracker.clear(attachment.id)
        }
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
