package com.example.firestationops.domain.catalog

import com.example.firestationops.domain.model.ApparatusStatus

data class StationCatalogInput(
    val name: String,
    val address: String? = null
)

data class ApparatusCatalogInput(
    val stationId: String,
    val name: String,
    val type: String,
    val radioName: String,
    val status: ApparatusStatus = ApparatusStatus.IN_SERVICE,
    val vin: String? = null,
    val licensePlate: String? = null,
    val barcode: String? = null
)

data class TemplateItemCatalogInput(
    val id: String? = null,
    val text: String,
    val category: String? = null,
    val isRequired: Boolean = true,
    val requiresNoteOnFail: Boolean = true
)

data class TemplateCatalogInput(
    val name: String,
    val description: String? = null,
    val apparatusType: String,
    val frequencyHours: Int = 24,
    val isActive: Boolean = true,
    val items: List<TemplateItemCatalogInput> = emptyList()
)
