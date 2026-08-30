package com.example.firestationops.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Department(
    val id: String,
    val name: String,
    val stationIds: List<String> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
