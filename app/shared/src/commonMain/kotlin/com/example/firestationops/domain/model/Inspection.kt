package com.example.firestationops.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Inspection(
    val id: String,
    val templateId: String,
    val apparatusId: String,
    val departmentId: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val startedByUserId: String,
    val responses: List<InspectionResponse> = emptyList(),
    val isFinalized: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val voidedAt: Long? = null,
    val voidedReason: String? = null
)

@Serializable
data class InspectionResponse(
    val itemId: String,
    val status: InspectionStatus,
    val note: String? = null,
    val severity: DeficiencySeverity? = null,
    val deficiencyId: String? = null,
    val attachmentIds: List<String> = emptyList()
)

@Serializable
enum class InspectionStatus {
    PASS,
    FAIL,
    NOT_APPLICABLE
}
