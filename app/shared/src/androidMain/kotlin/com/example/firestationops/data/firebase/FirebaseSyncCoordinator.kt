package com.example.firestationops.data.firebase

import android.content.Context
import android.util.Log
import com.example.firestationops.data.sync.DepartmentSyncEngine
import com.example.firestationops.domain.repository.AttachmentRepository
import com.example.firestationops.domain.repository.CatalogRepository
import com.example.firestationops.domain.repository.DeficiencyRepository
import com.example.firestationops.domain.repository.IncidentRepository
import com.example.firestationops.domain.repository.InspectionRepository
import com.example.firestationops.domain.sync.SyncCoordinator
import com.example.firestationops.domain.sync.SyncResult
import com.example.firestationops.domain.sync.SyncRunnerState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirebaseSyncCoordinator(
    context: Context,
    private val firebaseEnabled: Boolean,
    attachmentCache: com.example.firestationops.data.sync.SyncAttachmentCache,
    catalogRepository: CatalogRepository,
    attachmentRepository: AttachmentRepository,
    inspectionRepository: InspectionRepository,
    deficiencyRepository: DeficiencyRepository,
    incidentRepository: IncidentRepository,
    syncConflictRepository: com.example.firestationops.domain.repository.SyncConflictRepository
) : SyncCoordinator {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val syncEngine = DepartmentSyncEngine(
        cloudSyncClient = AndroidCloudSyncClient(),
        attachmentCache = attachmentCache,
        catalogRepository = catalogRepository,
        attachmentRepository = attachmentRepository,
        inspectionRepository = inspectionRepository,
        deficiencyRepository = deficiencyRepository,
        incidentRepository = incidentRepository,
        syncConflictRepository = syncConflictRepository
    )

    private val _syncState = MutableStateFlow(SyncRunnerState.IDLE)
    override val syncState: StateFlow<SyncRunnerState> = _syncState.asStateFlow()

    override fun isAvailable(): Boolean = firebaseEnabled

    override suspend fun syncDepartment(departmentId: String): SyncResult {
        if (!firebaseEnabled) {
            return SyncResult(errors = listOf("Firebase is not configured on this device."))
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            return SyncResult(
                errors = listOf(
                    "Cloud sync requires Firebase sign-in. Use Login (not Sign in offline) before uploading photos."
                )
            )
        }

        return try {
            runCatching { auth.syncMemberClaims(AUTH_TIMEOUT_MS) }
                .onFailure { error -> Log.w(TAG, "member claims sync skipped", error) }
            currentUser.getIdToken(true).await()
            Log.i(TAG, "sync start departmentId=$departmentId uid=${currentUser.uid}")
            _syncState.value = SyncRunnerState.RUNNING
            val result = syncEngine.syncDepartment(departmentId)
            _syncState.value = if (result.errors.isEmpty()) SyncRunnerState.IDLE else SyncRunnerState.FAILED
            result
        } catch (error: Exception) {
            Log.e(TAG, "sync auth/token failure", error)
            _syncState.value = SyncRunnerState.FAILED
            SyncResult(errors = listOf(error.message ?: "Firebase authentication failed before sync."))
        }
    }

    private companion object {
        const val TAG = "FirestationOpsSync"
        const val AUTH_TIMEOUT_MS = 30_000L
    }
}
