package com.example.firestationops.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SyncStatus {
    LOCAL_ONLY,
    PENDING_SYNC,
    SYNCED,
    SYNC_FAILED,
    CONFLICT
}
