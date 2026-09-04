package com.example.firestationops.domain

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.InspectionTemplate

/**
 * Resolves which inspection templates an apparatus may run.
 *
 * Prefer explicit [Apparatus.assignedTemplateIds] when set; otherwise fall back to
 * active templates matching [Apparatus.type].
 */
object TemplateAssignmentRules {

    fun resolveEligibleTemplates(
        apparatus: Apparatus,
        templates: List<InspectionTemplate>
    ): List<InspectionTemplate> {
        val active = templates.filter { it.isActive && it.departmentId == apparatus.departmentId }
        val assignedIds = apparatus.assignedTemplateIds.filter { it.isNotBlank() }
        if (assignedIds.isNotEmpty()) {
            val byId = active.associateBy { it.id }
            return assignedIds.mapNotNull { byId[it] }
        }
        return active.filter { it.apparatusType.equals(apparatus.type, ignoreCase = true) }
    }

    fun resolveDefaultTemplate(
        apparatus: Apparatus,
        templates: List<InspectionTemplate>
    ): InspectionTemplate? =
        resolveEligibleTemplates(apparatus, templates).firstOrNull()

    fun validateAssignedTemplateIds(
        assignedTemplateIds: List<String>,
        apparatusType: String,
        templates: List<InspectionTemplate>
    ): String? {
        if (assignedTemplateIds.isEmpty()) return null
        val byId = templates.associateBy { it.id }
        assignedTemplateIds.forEach { id ->
            val template = byId[id] ?: return "Assigned template was not found."
            if (!template.isActive) {
                return "Assigned template \"${template.name}\" is inactive."
            }
            if (!template.apparatusType.equals(apparatusType, ignoreCase = true)) {
                return "Template \"${template.name}\" is for ${template.apparatusType}, not $apparatusType."
            }
        }
        return null
    }
}
