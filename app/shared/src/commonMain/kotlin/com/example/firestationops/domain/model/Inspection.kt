package com.example.firestationops.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class InspectionEntrySource {
    FIELD,
    HISTORICAL_IMPORT
}

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
    val voidedReason: String? = null,
    val entrySource: InspectionEntrySource = InspectionEntrySource.FIELD,
    val odometerMiles: Int? = null,
    val fluidOil: String? = null,
    val fluidTransmission: String? = null,
    val fluidFuel: String? = null,
    val fluidAntifreeze: String? = null,
    val fluidPowerSteering: String? = null,
    val importedAt: Long? = null,
    val importedByUserId: String? = null
)

@Serializable
data class InspectionResponse(
    val itemId: String,
    val status: InspectionStatus,
    val note: String? = null,
    val severity: DeficiencySeverity? = null,
    val deficiencyId: String? = null,
    val attachmentIds: List<String> = emptyList(),
    val actualQuantity: Int? = null,
    /** Snapshot of template expected quantity at response time (for completed-record integrity). */
    val expectedQuantity: Int? = null
)

@Serializable
enum class InspectionStatus {
    PASS,
    FAIL,
    NOT_APPLICABLE
}
