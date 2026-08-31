package com.example.firestationops.domain.sync

data class SyncResult(
    val uploadedItems: List<SyncActivityItem> = emptyList(),
    val downloadedItems: List<SyncActivityItem> = emptyList(),
    val failedCount: Int = 0,
    val conflictCount: Int = 0,
    val errors: List<String> = emptyList()
) {
    val uploadedCount: Int get() = uploadedItems.size
    val downloadedCount: Int get() = downloadedItems.size
    val isSuccess: Boolean get() = failedCount == 0 && errors.isEmpty() && conflictCount == 0
    val hasPartialSuccess: Boolean get() = (failedCount > 0 || conflictCount > 0) && (uploadedCount > 0 || downloadedCount > 0)
    val hasChanges: Boolean get() = uploadedCount > 0 || downloadedCount > 0 || conflictCount > 0
}

enum class SyncRunnerState {
    IDLE,
    RUNNING,
    FAILED
}
