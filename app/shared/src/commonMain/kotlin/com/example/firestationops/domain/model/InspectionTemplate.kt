package com.example.firestationops.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class InspectionTemplate(
    val id: String,
    val departmentId: String,
    val name: String,
    val description: String? = null,
    val apparatusType: String, // Matches Apparatus.type
    val version: Int = 1,
    val isActive: Boolean = true,
    val items: List<InspectionTemplateItem> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
data class InspectionTemplateItem(
    val id: String,
    val text: String,
    val description: String? = null,
    val isRequired: Boolean = true,
    val requiresNoteOnFail: Boolean = true,
    val category: String? = null // e.g., "Engine", "Cab", "Tools"
)
