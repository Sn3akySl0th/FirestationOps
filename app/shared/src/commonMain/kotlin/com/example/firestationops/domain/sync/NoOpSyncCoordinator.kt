package com.example.firestationops.domain.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NoOpSyncCoordinator : SyncCoordinator {
    private val _syncState = MutableStateFlow(SyncRunnerState.IDLE)
    override val syncState: StateFlow<SyncRunnerState> = _syncState.asStateFlow()

    override suspend fun syncDepartment(departmentId: String): SyncResult =
        SyncResult(errors = listOf("Cloud sync is not configured on this platform."))

    override fun isAvailable(): Boolean = false
}
