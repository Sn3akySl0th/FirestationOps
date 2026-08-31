package com.example.firestationops.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Attachment(
    val id: String,
    val departmentId: String,
    val localUri: String? = null,
    val remoteUrl: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val createdAt: Long,
    val createdByUserId: String,
    val lastError: String? = null,
    val failedAt: Long? = null
)
