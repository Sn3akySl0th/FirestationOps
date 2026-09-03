package com.example.firestationops.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ApparatusStatus {
    IN_SERVICE,
    OUT_OF_SERVICE,
    MAINTENANCE,
    RESERVE
}

@Serializable
data class Apparatus(
    val id: String,
    val departmentId: String,
    val stationId: String,
    val name: String,
    val type: String, // e.g., "Engine", "Tanker", "Ladder"
    val radioName: String, // e.g., "Engine 1"
    val status: ApparatusStatus = ApparatusStatus.IN_SERVICE,
    val year: Int? = null,
    val make: String? = null,
    val model: String? = null,
    val vin: String? = null,
    val licensePlate: String? = null,
    val barcode: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
