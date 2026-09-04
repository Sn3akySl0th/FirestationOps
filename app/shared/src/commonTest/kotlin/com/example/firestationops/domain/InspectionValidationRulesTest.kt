package com.example.firestationops.domain

import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.InspectionResponse
import com.example.firestationops.domain.model.InspectionStatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.InspectionTemplateItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InspectionValidationRulesTest {

    private val gloves = InspectionTemplateItem(
        id = "gloves",
        text = "Boxes of Gloves",
        category = "O.S. Compartment 2",
        expectedQuantity = 4,
        requiresNoteOnFail = true
    )

    private val lights = InspectionTemplateItem(
        id = "lights",
        text = "Emergency Lights and Siren",
        category = "Cab",
        expectedQuantity = null,
        requiresNoteOnFail = true
    )

    private val template = InspectionTemplate(
        id = "tmpl-e5",
        departmentId = "dept-1",
        name = "Weekly Inventory Checkoff — Engine 5",
        apparatusType = "Engine",
        items = listOf(gloves, lights)
    )

    @Test
    fun actualQuantityBelowExpected_infersFail() {
        assertEquals(
            InspectionStatus.FAIL,
            InspectionValidationRules.inferredStatus(expectedQuantity = 4, actualQuantity = 2)
        )
    }

    @Test
    fun actualQuantityAtOrAboveExpected_infersPass() {
        assertEquals(
            InspectionStatus.PASS,
            InspectionValidationRules.inferredStatus(expectedQuantity = 4, actualQuantity = 4)
        )
        assertEquals(
            InspectionStatus.PASS,
            InspectionValidationRules.inferredStatus(expectedQuantity = 4, actualQuantity = 5)
        )
    }

    @Test
    fun withActualQuantity_shortCountSetsFail() {
        val updated = InspectionValidationRules.withActualQuantity(
            item = gloves,
            response = InspectionResponse(gloves.id, InspectionStatus.PASS),
            actualQuantity = 2
        )
        assertEquals(InspectionStatus.FAIL, updated.status)
        assertEquals(2, updated.actualQuantity)
        assertEquals(4, updated.expectedQuantity)
    }

    @Test
    fun withActualQuantity_keepsFailOverrideWhenCountMeetsExpectedAndNotePresent() {
        val updated = InspectionValidationRules.withActualQuantity(
            item = gloves,
            response = InspectionResponse(
                itemId = gloves.id,
                status = InspectionStatus.FAIL,
                note = "Boxes damaged"
            ),
            actualQuantity = 4
        )
        assertEquals(InspectionStatus.FAIL, updated.status)
        assertEquals(4, updated.actualQuantity)
    }

    @Test
    fun quantityItem_requiresActualQuantityOnSubmit() {
        val error = InspectionValidationRules.validateSubmission(
            template = template,
            responses = mapOf(
                gloves.id to InspectionResponse(gloves.id, InspectionStatus.PASS),
                lights.id to InspectionResponse(lights.id, InspectionStatus.PASS)
            )
        )
        assertNotNull(error)
        assertTrue(error.contains("quantity", ignoreCase = true))
    }

    @Test
    fun quantityItem_rejectsNegativeActualQuantity() {
        val error = InspectionValidationRules.validateQuantity(
            item = gloves,
            response = InspectionResponse(
                itemId = gloves.id,
                status = InspectionStatus.FAIL,
                actualQuantity = -1,
                expectedQuantity = 4,
                note = "Missing"
            )
        )
        assertNotNull(error)
        assertTrue(error.contains("negative", ignoreCase = true))
    }

    @Test
    fun quantityItem_shortCountWithoutFailStatus_isInvalid() {
        val error = InspectionValidationRules.validateQuantity(
            item = gloves,
            response = InspectionResponse(
                itemId = gloves.id,
                status = InspectionStatus.PASS,
                actualQuantity = 2,
                expectedQuantity = 4
            )
        )
        assertNotNull(error)
        assertTrue(error.contains("below expected", ignoreCase = true))
    }

    @Test
    fun quantityItem_shortCountWithFailAndNote_isValid() {
        assertNull(
            InspectionValidationRules.validateSubmission(
                template = template,
                responses = mapOf(
                    gloves.id to InspectionResponse(
                        itemId = gloves.id,
                        status = InspectionStatus.FAIL,
                        actualQuantity = 2,
                        expectedQuantity = 4,
                        note = "Two boxes missing"
                    ),
                    lights.id to InspectionResponse(lights.id, InspectionStatus.PASS)
                )
            )
        )
    }

    @Test
    fun presenceOnlyItem_doesNotRequireQuantity() {
        assertNull(
            InspectionValidationRules.validateQuantity(
                item = lights,
                response = InspectionResponse(lights.id, InspectionStatus.PASS)
            )
        )
    }

    @Test
    fun failWithoutNote_isInvalidWhenNoteRequired() {
        val error = InspectionValidationRules.validateFailNote(
            item = lights,
            response = InspectionResponse(lights.id, InspectionStatus.FAIL)
        )
        assertEquals("Notes are required for failed items.", error)
    }

    @Test
    fun outOfServiceWithoutNote_isInvalidEvenIfNoteNotRequiredOnItem() {
        val item = lights.copy(requiresNoteOnFail = false)
        val error = InspectionValidationRules.validateFailNote(
            item = item,
            response = InspectionResponse(
                itemId = item.id,
                status = InspectionStatus.FAIL,
                severity = DeficiencySeverity.OUT_OF_SERVICE
            )
        )
        assertEquals("Notes are required for failed items.", error)
    }

    @Test
    fun notApplicableQuantityItem_skipsQuantityRequirement() {
        assertNull(
            InspectionValidationRules.validateQuantity(
                item = gloves,
                response = InspectionResponse(gloves.id, InspectionStatus.NOT_APPLICABLE)
            )
        )
    }

    @Test
    fun isValid_falseWhenTemplateNull() {
        assertFalse(InspectionValidationRules.isValid(null, emptyMap()))
    }

    @Test
    fun fullCountPass_isValid() {
        assertTrue(
            InspectionValidationRules.isValid(
                template,
                mapOf(
                    gloves.id to InspectionResponse(
                        itemId = gloves.id,
                        status = InspectionStatus.PASS,
                        actualQuantity = 4,
                        expectedQuantity = 4
                    ),
                    lights.id to InspectionResponse(lights.id, InspectionStatus.PASS)
                )
            )
        )
    }
}
