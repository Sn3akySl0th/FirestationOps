package com.example.firestationops.domain.sync

data class SyncConflict(
    val id: String,
    val departmentId: String,
    val recordType: SyncConflictRecordType,
    val recordId: String,
    val localSnapshotJson: String,
    val remoteSnapshotJson: String,
    val detectedAt: Long
)

enum class SyncConflictResolution {
    KEEP_LOCAL,
    KEEP_REMOTE
}
