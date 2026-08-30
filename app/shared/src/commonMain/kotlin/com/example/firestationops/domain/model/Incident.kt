package com.example.firestationops.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class IncidentStatus {
    DRAFT,
    ACTIVE,
    CLOSED
}

@Serializable
enum class IncidentType {
    FIRE,
    RESCUE,
    HAZMAT,
    TRAINING,
    PUBLIC_SERVICE,
    OTHER
}

@Serializable
data class Incident(
    val id: String,
    val departmentId: String,
    val title: String,
    val summary: String = "",
    val locationDescription: String = "",
    val incidentType: IncidentType = IncidentType.OTHER,
    val status: IncidentStatus = IncidentStatus.DRAFT,
    val createdAt: Long,
    val createdByUserId: String,
    val updatedAt: Long,
    val updatedByUserId: String,
    val closedAt: Long? = null,
    val closedByUserId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)

@Serializable
enum class CommandLogEntryType {
    LOG,
    CORRECTION
}

@Serializable
data class CommandLogEntry(
    val id: String,
    val incidentId: String,
    val departmentId: String,
    val message: String,
    val entryType: CommandLogEntryType = CommandLogEntryType.LOG,
    val createdAt: Long,
    val createdByUserId: String,
    val incidentTimestamp: Long? = null,
    val correctsEntryId: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)
