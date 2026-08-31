package com.example.firestationops.data.firebase

import android.content.Context
import com.example.firestationops.domain.model.Attachment
import com.example.firestationops.domain.model.SyncStatus
import com.example.firestationops.domain.repository.AttachmentRepository
import com.example.firestationops.domain.repository.CatalogRepository
import com.example.firestationops.domain.repository.DeficiencyRepository
import com.example.firestationops.domain.repository.IncidentRepository
import com.example.firestationops.domain.repository.InspectionRepository
import com.example.firestationops.domain.sync.SyncCoordinator
import com.example.firestationops.domain.sync.SyncResult
import com.example.firestationops.domain.sync.SyncRunnerState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.io.File

class FirebaseSyncCoordinator(
    private val context: Context,
    private val firebaseEnabled: Boolean,
    private val catalogRepository: CatalogRepository,
    private val attachmentRepository: AttachmentRepository,
    private val inspectionRepository: InspectionRepository,
    private val deficiencyRepository: DeficiencyRepository,
    private val incidentRepository: IncidentRepository,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) : SyncCoordinator {
    private val _syncState = MutableStateFlow(SyncRunnerState.IDLE)
    override val syncState: StateFlow<SyncRunnerState> = _syncState.asStateFlow()

    override fun isAvailable(): Boolean = firebaseEnabled

    override suspend fun syncDepartment(departmentId: String): SyncResult {
        if (!firebaseEnabled) {
            return SyncResult(errors = listOf("Firebase is not configured on this device."))
        }

        _syncState.value = SyncRunnerState.RUNNING
        var uploadedCount = 0
        var downloadedCount = 0
        var failedCount = 0
        val errors = mutableListOf<String>()

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
            downloadedCount += pullDepartmentCatalog(departmentId)
            catalogRepository.notifyCatalogUpdated()
        }
        runStep("Download inspections") {
            downloadedCount += pullInspections(departmentId)
        }
        runStep("Download deficiencies") {
            downloadedCount += pullDeficiencies(departmentId)
        }
        runStep("Download incidents") {
            downloadedCount += pullIncidents(departmentId)
        }
        runStep("Download attachments") {
            downloadedCount += pullAttachments(departmentId)
        }

        val pendingAttachments = attachmentRepository.getPendingSyncAttachments()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId }

        pendingAttachments.forEach { attachment ->
            runStep("Attachment ${attachment.id}") {
                uploadAttachment(attachment)
                uploadedCount++
            }
        }

        val pendingInspections = inspectionRepository.getPendingSyncInspections()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId && it.isFinalized }

        pendingInspections.forEach { inspection ->
            runStep("Inspection ${inspection.id}") {
                firestore.document(FirestorePaths.inspection(departmentId, inspection.id))
                    .set(FirestoreMappers.inspectionToMap(inspection), SetOptions.merge())
                    .await()
                inspectionRepository.updateSyncStatus(inspection.id, SyncStatus.SYNCED)
                uploadedCount++
            }
        }

        val pendingDeficiencies = deficiencyRepository.getPendingSyncDeficiencies()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId }

        pendingDeficiencies.forEach { deficiency ->
            runStep("Deficiency ${deficiency.id}") {
                firestore.document(FirestorePaths.deficiency(departmentId, deficiency.id))
                    .set(FirestoreMappers.deficiencyToMap(deficiency), SetOptions.merge())
                    .await()
                deficiencyRepository.updateSyncStatus(deficiency.id, SyncStatus.SYNCED)
                uploadedCount++
            }
        }

        val pendingIncidents = incidentRepository.getPendingSyncIncidents()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId }

        pendingIncidents.forEach { incident ->
            runStep("Incident ${incident.id}") {
                firestore.document(FirestorePaths.incident(departmentId, incident.id))
                    .set(FirestoreMappers.incidentToMap(incident), SetOptions.merge())
                    .await()
                incidentRepository.updateIncidentSyncStatus(incident.id, SyncStatus.SYNCED)
                uploadedCount++
            }
        }

        incidentRepository.getPendingSyncCommandLogEntries()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId }
            .forEach { entry ->
                runStep("Command log ${entry.id}") {
                    firestore.document(
                        FirestorePaths.commandLogEntry(departmentId, entry.incidentId, entry.id)
                    )
                        .set(FirestoreMappers.commandLogEntryToMap(entry), SetOptions.merge())
                        .await()
                    incidentRepository.updateCommandLogEntrySyncStatus(entry.id, SyncStatus.SYNCED)
                    uploadedCount++
                }
            }

        incidentRepository.getPendingSyncUnitAssignments()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId }
            .forEach { assignment ->
                runStep("Unit assignment ${assignment.id}") {
                    firestore.document(
                        FirestorePaths.unitAssignment(departmentId, assignment.incidentId, assignment.id)
                    )
                        .set(FirestoreMappers.unitAssignmentToMap(assignment), SetOptions.merge())
                        .await()
                    incidentRepository.updateUnitAssignmentSyncStatus(assignment.id, SyncStatus.SYNCED)
                    uploadedCount++
                }
            }

        incidentRepository.getPendingSyncPersonnelAssignments()
            .getOrElse { emptyList() }
            .filter { it.departmentId == departmentId }
            .forEach { assignment ->
                runStep("Personnel assignment ${assignment.id}") {
                    firestore.document(
                        FirestorePaths.personnelAssignment(departmentId, assignment.incidentId, assignment.id)
                    )
                        .set(FirestoreMappers.personnelAssignmentToMap(assignment), SetOptions.merge())
                        .await()
                    incidentRepository.updatePersonnelAssignmentSyncStatus(assignment.id, SyncStatus.SYNCED)
                    uploadedCount++
                }
            }

        _syncState.value = if (errors.isEmpty()) SyncRunnerState.IDLE else SyncRunnerState.FAILED
        return SyncResult(
            uploadedCount = uploadedCount,
            downloadedCount = downloadedCount,
            failedCount = failedCount,
            errors = errors
        )
    }

    private suspend fun pullDepartmentCatalog(departmentId: String): Int {
        var count = 0

        val departmentSnapshot = firestore.document(FirestorePaths.department(departmentId)).get().await()
        if (departmentSnapshot.exists()) {
            val department = FirestoreMappers.departmentFromMap(
                id = departmentId,
                data = departmentSnapshot.data ?: emptyMap()
            )
            if (department != null) {
                catalogRepository.applyDepartment(department)
                count++
            }
        }

        val stationSnapshot = firestore.collection("departments")
            .document(departmentId)
            .collection("stations")
            .get()
            .await()
        for (document in stationSnapshot.documents) {
            val station = FirestoreMappers.stationFromMap(document.id, document.data ?: continue) ?: continue
            catalogRepository.applyStation(station)
            count++
        }

        val apparatusSnapshot = firestore.collection("departments")
            .document(departmentId)
            .collection("apparatus")
            .get()
            .await()
        for (document in apparatusSnapshot.documents) {
            val apparatus = FirestoreMappers.apparatusFromMap(document.id, document.data ?: continue) ?: continue
            catalogRepository.applyApparatus(apparatus)
            count++
        }

        val templateSnapshot = firestore.collection("departments")
            .document(departmentId)
            .collection("templates")
            .get()
            .await()
        for (document in templateSnapshot.documents) {
            val template = FirestoreMappers.templateFromMap(document.id, document.data ?: continue) ?: continue
            catalogRepository.applyTemplate(template)
            count++
        }

        val memberSnapshot = firestore.collection("departments")
            .document(departmentId)
            .collection("members")
            .get()
            .await()
        for (document in memberSnapshot.documents) {
            val member = FirestoreMappers.memberFromMap(document.id, document.data ?: continue) ?: continue
            catalogRepository.applyMember(member)
            count++
        }

        return count
    }

    private suspend fun pullInspections(departmentId: String): Int {
        val pendingLocalIds = inspectionRepository.getPendingSyncInspections()
            .getOrElse { emptyList() }
            .map { it.id }
            .toSet()

        val snapshot = firestore.collection("departments")
            .document(departmentId)
            .collection("inspections")
            .get()
            .await()

        var count = 0
        for (document in snapshot.documents) {
            val inspection = FirestoreMappers.inspectionFromMap(
                id = document.id,
                data = document.data ?: continue
            ) ?: continue

            if (inspection.id in pendingLocalIds) continue

            inspectionRepository.saveInspection(inspection)
            count++
        }
        return count
    }

    private suspend fun pullDeficiencies(departmentId: String): Int {
        val pendingLocalIds = deficiencyRepository.getPendingSyncDeficiencies()
            .getOrElse { emptyList() }
            .map { it.id }
            .toSet()

        val snapshot = firestore.collection("departments")
            .document(departmentId)
            .collection("deficiencies")
            .get()
            .await()

        var count = 0
        for (document in snapshot.documents) {
            val deficiency = FirestoreMappers.deficiencyFromMap(
                id = document.id,
                data = document.data ?: continue
            ) ?: continue

            if (deficiency.id in pendingLocalIds) continue

            deficiencyRepository.saveDeficiency(deficiency)
            count++
        }
        return count
    }

    private suspend fun pullIncidents(departmentId: String): Int {
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

        val snapshot = firestore.collection("departments")
            .document(departmentId)
            .collection("incidents")
            .get()
            .await()

        var count = 0
        for (document in snapshot.documents) {
            val incident = FirestoreMappers.incidentFromMap(
                id = document.id,
                data = document.data ?: continue
            ) ?: continue

            if (incident.id !in pendingIncidentIds) {
                incidentRepository.saveIncident(incident)
                count++
            }

            val incidentRef = firestore.collection("departments")
                .document(departmentId)
                .collection("incidents")
                .document(incident.id)

            val commandLogSnapshot = incidentRef.collection("commandLog").get().await()
            for (entryDocument in commandLogSnapshot.documents) {
                val entry = FirestoreMappers.commandLogEntryFromMap(
                    id = entryDocument.id,
                    data = entryDocument.data ?: continue
                ) ?: continue
                if (entry.id in pendingCommandLogIds) continue
                incidentRepository.appendCommandLogEntry(entry)
                count++
            }

            val unitAssignmentSnapshot = incidentRef.collection("unitAssignments").get().await()
            for (assignmentDocument in unitAssignmentSnapshot.documents) {
                val assignment = FirestoreMappers.unitAssignmentFromMap(
                    id = assignmentDocument.id,
                    data = assignmentDocument.data ?: continue
                ) ?: continue
                if (assignment.id in pendingUnitAssignmentIds) continue
                incidentRepository.saveUnitAssignment(assignment)
                count++
            }

            val personnelAssignmentSnapshot = incidentRef.collection("personnelAssignments").get().await()
            for (assignmentDocument in personnelAssignmentSnapshot.documents) {
                val assignment = FirestoreMappers.personnelAssignmentFromMap(
                    id = assignmentDocument.id,
                    data = assignmentDocument.data ?: continue
                ) ?: continue
                if (assignment.id in pendingPersonnelAssignmentIds) continue
                incidentRepository.savePersonnelAssignment(assignment)
                count++
            }
        }
        return count
    }

    private suspend fun pullAttachments(departmentId: String): Int {
        val pendingLocalIds = attachmentRepository.getPendingSyncAttachments()
            .getOrElse { emptyList() }
            .map { it.id }
            .toSet()

        val snapshot = firestore.collection("departments")
            .document(departmentId)
            .collection("attachments")
            .get()
            .await()

        val cacheDir = File(context.cacheDir, "sync_attachments").apply { mkdirs() }
        var count = 0

        for (document in snapshot.documents) {
            val remoteAttachment = FirestoreMappers.attachmentFromMap(
                id = document.id,
                data = document.data ?: continue
            ) ?: continue

            if (remoteAttachment.id in pendingLocalIds) continue

            val existing = attachmentRepository.getAttachment(remoteAttachment.id).getOrNull()
            val existingLocalPath = existing?.localUri
            if (!existingLocalPath.isNullOrBlank() && File(existingLocalPath).exists()) {
                attachmentRepository.saveAttachment(
                    remoteAttachment.copy(localUri = existingLocalPath, syncStatus = SyncStatus.SYNCED)
                )
                continue
            }

            val localFile = File(cacheDir, "${remoteAttachment.id}.jpg")
            val storagePath = FirestorePaths.attachmentStorage(departmentId, remoteAttachment.id)
            storage.reference.child(storagePath).getFile(localFile).await()

            attachmentRepository.saveAttachment(
                remoteAttachment.copy(
                    localUri = localFile.absolutePath,
                    syncStatus = SyncStatus.SYNCED
                )
            )
            count++
        }
        return count
    }

    private suspend fun uploadAttachment(attachment: Attachment) {
        val localPath = attachment.localUri
            ?: error("Attachment ${attachment.id} has no local file path.")

        val file = File(localPath)
        if (!file.exists()) {
            attachmentRepository.updateSyncStatus(attachment.id, SyncStatus.SYNC_FAILED)
            error("Attachment file not found at $localPath")
        }

        val storagePath = FirestorePaths.attachmentStorage(attachment.departmentId, attachment.id)
        val downloadUrl = storage.reference.child(storagePath)
            .putFile(android.net.Uri.fromFile(file))
            .await()
            .storage
            .downloadUrl
            .await()
            .toString()

        firestore.document(FirestorePaths.attachment(attachment.departmentId, attachment.id))
            .set(
                FirestoreMappers.attachmentToMap(
                    attachment.copy(remoteUrl = downloadUrl, syncStatus = SyncStatus.SYNCED)
                ),
                SetOptions.merge()
            )
            .await()

        attachmentRepository.updateRemoteUrl(attachment.id, downloadUrl)
    }
}
