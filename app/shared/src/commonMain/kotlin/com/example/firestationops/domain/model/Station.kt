package com.example.firestationops.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Station(
    val id: String,
    val departmentId: String,
    val name: String,
    val address: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)
