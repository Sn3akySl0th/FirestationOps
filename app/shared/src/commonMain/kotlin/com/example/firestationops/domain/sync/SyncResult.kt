package com.example.firestationops.domain.sync

data class SyncResult(
    val uploadedCount: Int = 0,
    val downloadedCount: Int = 0,
    val failedCount: Int = 0,
    val errors: List<String> = emptyList()
) {
    val isSuccess: Boolean get() = failedCount == 0 && errors.isEmpty()
}

enum class SyncRunnerState {
    IDLE,
    RUNNING,
    FAILED
}
