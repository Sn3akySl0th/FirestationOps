package com.example.firestationops.domain.sync

import kotlinx.coroutines.flow.StateFlow

interface SyncCoordinator {
    val syncState: StateFlow<SyncRunnerState>

    suspend fun syncDepartment(departmentId: String): SyncResult

    fun isAvailable(): Boolean
}
