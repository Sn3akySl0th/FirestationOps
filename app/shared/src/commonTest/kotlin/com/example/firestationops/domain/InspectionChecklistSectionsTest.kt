package com.example.firestationops.domain

import com.example.firestationops.domain.model.InspectionResponse
import com.example.firestationops.domain.model.InspectionStatus
import com.example.firestationops.domain.model.InspectionTemplateItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InspectionChecklistSectionsTest {

    private val gloves = InspectionTemplateItem(
        id = "gloves",
        text = "Boxes of Gloves",
        category = "O.S. Compartment 2",
        expectedQuantity = 4
    )
    private val aed = InspectionTemplateItem(
        id = "aed",
        text = "AED",
        category = "O.S. Compartment 2",
        expectedQuantity = 1
    )
    private val lights = InspectionTemplateItem(
        id = "lights",
        text = "Emergency Lights",
        category = "Cab",
        expectedQuantity = null
    )

    @Test
    fun build_groupsByCategoryPreservingOrder() {
        val progress = InspectionChecklistSections.build(
            items = listOf(gloves, aed, lights),
            responses = emptyMap()
        )
        assertEquals(listOf("O.S. Compartment 2", "Cab"), progress.sections.map { it.category })
        assertEquals(0, progress.completedCount)
        assertEquals(3, progress.totalCount)
    }

    @Test
    fun quantityItem_incompleteUntilActualEntered() {
        assertFalse(
            InspectionChecklistSections.isItemComplete(
                gloves,
                InspectionResponse(gloves.id, InspectionStatus.PASS, expectedQuantity = 4)
            )
        )
        assertTrue(
            InspectionChecklistSections.isItemComplete(
                gloves,
                InspectionResponse(
                    itemId = gloves.id,
                    status = InspectionStatus.PASS,
                    actualQuantity = 4,
                    expectedQuantity = 4
                )
            )
        )
    }

    @Test
    fun shortCountWithNote_countsComplete() {
        assertTrue(
            InspectionChecklistSections.isItemComplete(
                gloves,
                InspectionResponse(
                    itemId = gloves.id,
                    status = InspectionStatus.FAIL,
                    actualQuantity = 2,
                    expectedQuantity = 4,
                    note = "Two missing"
                )
            )
        )
    }

    @Test
    fun markSectionPresent_fillsExpectedQuantities() {
        val updated = InspectionChecklistSections.markSectionPresent(
            category = "O.S. Compartment 2",
            items = listOf(gloves, aed, lights),
            responses = mapOf(
                gloves.id to InspectionResponse(gloves.id, InspectionStatus.PASS, expectedQuantity = 4),
                aed.id to InspectionResponse(aed.id, InspectionStatus.PASS, expectedQuantity = 1),
                lights.id to InspectionResponse(lights.id, InspectionStatus.PASS)
            )
        )
        assertEquals(4, updated[gloves.id]?.actualQuantity)
        assertEquals(InspectionStatus.PASS, updated[gloves.id]?.status)
        assertEquals(1, updated[aed.id]?.actualQuantity)
        assertEquals(InspectionStatus.PASS, updated[lights.id]?.status)

        val progress = InspectionChecklistSections.build(listOf(gloves, aed, lights), updated)
        assertEquals(3, progress.completedCount)
        assertEquals("2 / 2 complete", progress.sections.first().progressLabel)
    }

    @Test
    fun markSectionPresent_keepsDamageOverrideAtFullCount() {
        val updated = InspectionChecklistSections.markSectionPresent(
            category = "O.S. Compartment 2",
            items = listOf(gloves),
            responses = mapOf(
                gloves.id to InspectionResponse(
                    itemId = gloves.id,
                    status = InspectionStatus.FAIL,
                    actualQuantity = 4,
                    expectedQuantity = 4,
                    note = "Boxes damaged"
                )
            )
        )
        assertEquals(InspectionStatus.FAIL, updated[gloves.id]?.status)
        assertEquals("Boxes damaged", updated[gloves.id]?.note)
    }
}
