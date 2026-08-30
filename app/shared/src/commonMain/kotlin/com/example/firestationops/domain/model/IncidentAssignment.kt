package com.example.firestationops.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AssignmentStatus {
    ASSIGNED,
    EN_ROUTE,
    ON_SCENE,
    RELEASED
}

@Serializable
data class IncidentUnitAssignment(
    val id: String,
    val incidentId: String,
    val departmentId: String,
    val apparatusId: String,
    val status: AssignmentStatus = AssignmentStatus.ASSIGNED,
    val assignedAt: Long,
    val assignedByUserId: String,
    val updatedAt: Long,
    val updatedByUserId: String,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)

@Serializable
data class PersonnelAssignment(
    val id: String,
    val incidentId: String,
    val departmentId: String,
    val memberId: String,
    val status: AssignmentStatus = AssignmentStatus.ASSIGNED,
    val assignedAt: Long,
    val assignedByUserId: String,
    val updatedAt: Long,
    val updatedByUserId: String,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)
