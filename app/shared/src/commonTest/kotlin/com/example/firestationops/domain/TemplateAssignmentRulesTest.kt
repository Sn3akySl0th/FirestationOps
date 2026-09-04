package com.example.firestationops.domain

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.InspectionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemplateAssignmentRulesTest {

    private val daily = InspectionTemplate(
        id = "tmpl-daily",
        departmentId = "dept-1",
        name = "Daily Engine Inspection",
        apparatusType = "Engine",
        frequencyHours = 24
    )
    private val weekly = InspectionTemplate(
        id = "tmpl-weekly",
        departmentId = "dept-1",
        name = "Weekly Inventory Checkoff",
        apparatusType = "Engine",
        frequencyHours = 168
    )
    private val ladder = InspectionTemplate(
        id = "tmpl-ladder",
        departmentId = "dept-1",
        name = "Weekly Ladder Inspection",
        apparatusType = "Ladder",
        frequencyHours = 168
    )

    private val engine = Apparatus(
        id = "ap-1",
        departmentId = "dept-1",
        stationId = "st-1",
        name = "Engine 5",
        type = "Engine",
        radioName = "ENGINE 5"
    )

    @Test
    fun withoutAssignment_returnsActiveTemplatesForType() {
        val eligible = TemplateAssignmentRules.resolveEligibleTemplates(
            apparatus = engine,
            templates = listOf(daily, weekly, ladder)
        )
        assertEquals(listOf(daily, weekly), eligible)
    }

    @Test
    fun withAssignment_returnsOnlyAssignedActiveTemplatesInOrder() {
        val eligible = TemplateAssignmentRules.resolveEligibleTemplates(
            apparatus = engine.copy(assignedTemplateIds = listOf(weekly.id, daily.id)),
            templates = listOf(daily, weekly, ladder)
        )
        assertEquals(listOf(weekly, daily), eligible)
    }

    @Test
    fun withAssignment_skipsMissingOrInactiveTemplates() {
        val inactiveWeekly = weekly.copy(isActive = false)
        val eligible = TemplateAssignmentRules.resolveEligibleTemplates(
            apparatus = engine.copy(assignedTemplateIds = listOf(weekly.id, "missing")),
            templates = listOf(daily, inactiveWeekly)
        )
        assertTrue(eligible.isEmpty())
    }

    @Test
    fun validateAssignedTemplateIds_rejectsWrongType() {
        val error = TemplateAssignmentRules.validateAssignedTemplateIds(
            assignedTemplateIds = listOf(ladder.id),
            apparatusType = "Engine",
            templates = listOf(ladder)
        )
        assertTrue(error!!.contains("Ladder"))
    }

    @Test
    fun validateAssignedTemplateIds_allowsEmpty() {
        assertNull(
            TemplateAssignmentRules.validateAssignedTemplateIds(
                assignedTemplateIds = emptyList(),
                apparatusType = "Engine",
                templates = listOf(daily)
            )
        )
    }
}
