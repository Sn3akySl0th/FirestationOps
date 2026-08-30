package com.example.firestationops.data.firebase

import com.example.firestationops.domain.model.SyncStatus
import com.example.firestationops.domain.repository.AttachmentRepository
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
    private val firebaseEnabled: Boolean,
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

        runStep("Download inspections") {
            downloadedCount += pullInspections(departmentId)
        }
        runStep("Download deficiencies") {
            downloadedCount += pullDeficiencies(departmentId)
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

    private suspend fun uploadAttachment(attachment: com.example.firestationops.domain.model.Attachment) {
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
