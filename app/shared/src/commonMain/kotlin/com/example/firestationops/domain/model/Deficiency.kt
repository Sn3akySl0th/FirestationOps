package com.example.firestationops.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Deficiency(
    val id: String,
    val inspectionId: String? = null,
    val apparatusId: String,
    val departmentId: String,
    val title: String,
    val description: String,
    val severity: DeficiencySeverity,
    val status: DeficiencyStatus,
    val createdAt: Long,
    val createdByUserId: String,
    val resolvedAt: Long? = null,
    val resolvedByUserId: String? = null,
    val resolutionNote: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val attachmentIds: List<String> = emptyList()
)

@Serializable
enum class DeficiencySeverity {
    INFORMATIONAL,
    REPAIR_NEEDED,
    OUT_OF_SERVICE
}

@Serializable
enum class DeficiencyStatus {
    OPEN,
    ASSIGNED,
    RESOLVED,
    VOIDED
}
