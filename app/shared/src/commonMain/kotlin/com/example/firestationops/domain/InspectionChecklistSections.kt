package com.example.firestationops.domain

import com.example.firestationops.domain.model.InspectionResponse
import com.example.firestationops.domain.model.InspectionStatus
import com.example.firestationops.domain.model.InspectionTemplateItem

/**
 * Groups checklist items by category and computes section / overall progress
 * for large stocking inspections.
 */
object InspectionChecklistSections {

    const val UNCATEGORIZED = "General"

    data class Section(
        val category: String,
        val items: List<InspectionTemplateItem>,
        val completedCount: Int,
        val totalCount: Int
    ) {
        val isComplete: Boolean get() = completedCount >= totalCount && totalCount > 0
        val progressLabel: String get() = "$completedCount / $totalCount complete"
    }

    data class Progress(
        val sections: List<Section>,
        val completedCount: Int,
        val totalCount: Int
    ) {
        val fraction: Float
            get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount.toFloat()
        val progressLabel: String get() = "$completedCount / $totalCount complete"
    }

    fun build(
        items: List<InspectionTemplateItem>,
        responses: Map<String, InspectionResponse>
    ): Progress {
        val sections = linkedMapOf<String, MutableList<InspectionTemplateItem>>()
        items.forEach { item ->
            val key = item.category?.trim()?.takeIf { it.isNotEmpty() } ?: UNCATEGORIZED
            sections.getOrPut(key) { mutableListOf() }.add(item)
        }

        val built = sections.map { (category, sectionItems) ->
            val completed = sectionItems.count { item ->
                isItemComplete(item, responses[item.id])
            }
            Section(
                category = category,
                items = sectionItems,
                completedCount = completed,
                totalCount = sectionItems.size
            )
        }

        return Progress(
            sections = built,
            completedCount = built.sumOf { it.completedCount },
            totalCount = built.sumOf { it.totalCount }
        )
    }

    fun isItemComplete(item: InspectionTemplateItem, response: InspectionResponse?): Boolean {
        if (response == null) return false
        if (InspectionValidationRules.validateResponse(item, response) != null) return false
        if (item.expectedQuantity != null &&
            response.status != InspectionStatus.NOT_APPLICABLE &&
            response.actualQuantity == null
        ) {
            return false
        }
        return true
    }

    /**
     * Marks every quantity item in [category] as present at expected count (PASS).
     * Presence-only items are set to PASS. Does not clear existing FAIL notes on
     * items the crew already marked damaged at full count — those stay FAIL.
     */
    fun markSectionPresent(
        category: String,
        items: List<InspectionTemplateItem>,
        responses: Map<String, InspectionResponse>
    ): Map<String, InspectionResponse> {
        val updated = responses.toMutableMap()
        val sectionItems = items.filter {
            val key = it.category?.trim()?.takeIf { c -> c.isNotEmpty() } ?: UNCATEGORIZED
            key == category
        }
        sectionItems.forEach { item ->
            val current = updated[item.id] ?: InspectionResponse(
                itemId = item.id,
                status = InspectionStatus.PASS,
                expectedQuantity = item.expectedQuantity
            )
            val expected = item.expectedQuantity
            if (expected != null) {
                if (current.status == InspectionStatus.FAIL && !current.note.isNullOrBlank() &&
                    (current.actualQuantity ?: 0) >= expected
                ) {
                    // Keep damage override
                    return@forEach
                }
                updated[item.id] = InspectionValidationRules.withActualQuantity(item, current, expected)
            } else if (current.status != InspectionStatus.NOT_APPLICABLE) {
                updated[item.id] = current.copy(
                    status = InspectionStatus.PASS,
                    severity = null,
                    expectedQuantity = null
                )
            }
        }
        return updated
    }
}
