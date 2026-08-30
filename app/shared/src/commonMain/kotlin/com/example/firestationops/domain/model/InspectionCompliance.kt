package com.example.firestationops.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class InspectionComplianceStatus {
    CURRENT,
    DUE_SOON,
    OVERDUE,
    NEVER_INSPECTED,
    IN_PROGRESS
}

@Serializable
data class ApparatusInspectionStatus(
    val apparatusId: String,
    val templateId: String?,
    val templateName: String?,
    val status: InspectionComplianceStatus,
    val lastCompletedAt: Long?,
    val dueAt: Long?,
    val daysOverdue: Int = 0,
    val draftInspectionId: String? = null
)

data class DeficiencySummary(
    val total: Int,
    val outOfService: Int,
    val repairNeeded: Int,
    val informational: Int,
    val oldestOpenAt: Long?
)
