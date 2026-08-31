package com.example.firestationops.domain.sync

data class SyncResult(
    val uploadedCount: Int = 0,
    val downloadedCount: Int = 0,
    val failedCount: Int = 0,
    val errors: List<String> = emptyList()
) {
    val isSuccess: Boolean get() = failedCount == 0 && errors.isEmpty()
    val hasPartialSuccess: Boolean get() = failedCount > 0 && (uploadedCount > 0 || downloadedCount > 0)
}

enum class SyncRunnerState {
    IDLE,
    RUNNING,
    FAILED
}
