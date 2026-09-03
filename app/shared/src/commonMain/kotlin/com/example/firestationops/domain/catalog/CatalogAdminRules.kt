package com.example.firestationops.domain.catalog

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import com.example.firestationops.domain.model.Station

object CatalogAdminRules {
    fun requireAdmin(actor: Member): String? =
        if (actor.hasRole(Role.ADMIN)) {
            null
        } else {
            "Only administrators can manage the department catalog."
        }

    fun validateStationInput(input: StationCatalogInput): String? {
        if (input.name.trim().isBlank()) {
            return "Station name is required."
        }
        return null
    }

    fun validateApparatusInput(
        input: ApparatusCatalogInput,
        stations: List<Station>,
        existingApparatus: List<Apparatus> = emptyList(),
        editingApparatusId: String? = null
    ): String? {
        if (input.name.trim().isBlank()) {
            return "Apparatus name is required."
        }
        if (input.type.trim().isBlank()) {
            return "Apparatus type is required."
        }
        if (input.radioName.trim().isBlank()) {
            return "Radio name is required."
        }
        if (stations.none { it.id == input.stationId }) {
            return "Select a valid station."
        }
        val barcode = input.barcode?.trim()?.takeIf { it.isNotEmpty() }
        if (barcode != null) {
            val duplicate = existingApparatus.any { apparatus ->
                apparatus.id != editingApparatusId &&
                    apparatus.barcode?.trim()?.equals(barcode, ignoreCase = true) == true
            }
            if (duplicate) {
                return "Another apparatus already uses this barcode."
            }
        }
        return null
    }

    fun validateTemplateInput(input: TemplateCatalogInput): String? {
        if (input.name.trim().isBlank()) {
            return "Template name is required."
        }
        if (input.apparatusType.trim().isBlank()) {
            return "Apparatus type is required."
        }
        if (input.frequencyHours <= 0) {
            return "Inspection frequency must be greater than zero hours."
        }
        val nonBlankItems = input.items.map { it.text.trim() }.filter { it.isNotEmpty() }
        if (nonBlankItems.isEmpty()) {
            return "Add at least one checklist item."
        }
        return null
    }

    fun canDeactivateTemplate(
        template: InspectionTemplate,
        departmentTemplates: List<InspectionTemplate>
    ): Boolean {
        if (template.isActive) return true
        val activeForType = departmentTemplates.count {
            it.id != template.id &&
                it.apparatusType == template.apparatusType &&
                it.isActive
        }
        return activeForType > 0
    }

    fun apparatusCountForStation(stationId: String, apparatus: List<Apparatus>): Int =
        apparatus.count { it.stationId == stationId }
}
