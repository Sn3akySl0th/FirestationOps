package com.example.firestationops.domain

import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.InspectionResponse
import com.example.firestationops.domain.model.InspectionStatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.InspectionTemplateItem

/**
 * Domain rules for inspection responses, including stocking quantity checks.
 */
object InspectionValidationRules {

    fun requiresActualQuantity(item: InspectionTemplateItem): Boolean =
        item.expectedQuantity != null

    fun inferredStatus(expectedQuantity: Int, actualQuantity: Int): InspectionStatus =
        if (actualQuantity < expectedQuantity) InspectionStatus.FAIL else InspectionStatus.PASS

    /**
     * Applies an on-hand count. Infers PASS/FAIL from expected vs actual unless the crew
     * already marked FAIL for a non-quantity reason (e.g. damaged) while count meets expected.
     */
    fun withActualQuantity(
        item: InspectionTemplateItem,
        response: InspectionResponse,
        actualQuantity: Int
    ): InspectionResponse {
        val expected = item.expectedQuantity
        val status = when {
            expected == null -> response.status
            actualQuantity < expected -> InspectionStatus.FAIL
            response.status == InspectionStatus.FAIL && !response.note.isNullOrBlank() -> InspectionStatus.FAIL
            response.status == InspectionStatus.NOT_APPLICABLE -> InspectionStatus.NOT_APPLICABLE
            else -> InspectionStatus.PASS
        }
        return response.copy(
            status = status,
            actualQuantity = actualQuantity,
            expectedQuantity = expected
        )
    }

    fun validateQuantity(item: InspectionTemplateItem, response: InspectionResponse): String? {
        val expected = item.expectedQuantity ?: return null
        if (response.status == InspectionStatus.NOT_APPLICABLE) return null
        val actual = response.actualQuantity
            ?: return "Enter quantity found for \"${item.text}\" (expected $expected)."
        if (actual < 0) {
            return "Quantity for \"${item.text}\" cannot be negative."
        }
        if (actual < expected && response.status != InspectionStatus.FAIL) {
            return "Quantity for \"${item.text}\" is below expected ($actual / $expected)."
        }
        return null
    }

    fun requiresFailNote(item: InspectionTemplateItem?, response: InspectionResponse): Boolean {
        if (response.status != InspectionStatus.FAIL) return false
        return item?.requiresNoteOnFail == true || response.severity == DeficiencySeverity.OUT_OF_SERVICE
    }

    fun validateFailNote(item: InspectionTemplateItem?, response: InspectionResponse): String? {
        if (!requiresFailNote(item, response)) return null
        if (response.note.isNullOrBlank()) {
            return "Notes are required for failed items."
        }
        return null
    }

    fun validateResponse(item: InspectionTemplateItem, response: InspectionResponse): String? =
        validateQuantity(item, response) ?: validateFailNote(item, response)

    fun validateSubmission(
        template: InspectionTemplate,
        responses: Map<String, InspectionResponse>
    ): String? {
        for (item in template.items) {
            val response = responses[item.id] ?: continue
            validateResponse(item, response)?.let { return it }
        }
        // Responses for unknown item ids still need fail-note checks
        for (response in responses.values) {
            val item = template.items.find { it.id == response.itemId }
            validateFailNote(item, response)?.let { return it }
        }
        return null
    }

    fun isValid(
        template: InspectionTemplate?,
        responses: Map<String, InspectionResponse>
    ): Boolean {
        if (template == null) return false
        return validateSubmission(template, responses) == null
    }
}
