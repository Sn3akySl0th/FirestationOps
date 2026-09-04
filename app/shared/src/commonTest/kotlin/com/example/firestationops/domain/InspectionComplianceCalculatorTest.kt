package com.example.firestationops.domain

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.InspectionComplianceStatus
import com.example.firestationops.domain.model.InspectionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InspectionComplianceCalculatorTest {

    private val engineTemplate = InspectionTemplate(
        id = "tmpl-engine",
        departmentId = "dept-1",
        name = "Daily Engine Inspection",
        apparatusType = "Engine",
        frequencyHours = 24
    )

    private val engine = Apparatus(
        id = "ap-1",
        departmentId = "dept-1",
        stationId = "st-1",
        name = "Engine 1",
        type = "Engine",
        radioName = "E1"
    )

    @Test
    fun neverInspected_isNeverInspected() {
        val result = InspectionComplianceCalculator.calculate(
            apparatus = engine,
            template = engineTemplate,
            latestFinalized = null,
            draft = null,
            nowMillis = 1_000_000L
        )

        assertEquals(InspectionComplianceStatus.NEVER_INSPECTED, result?.status)
    }

    @Test
    fun completedWithinInterval_isCurrent() {
        val now = 100_000_000L
        val completedAt = now - (12 * 3_600_000L)
        val inspection = finalizedInspection(completedAt)

        val result = InspectionComplianceCalculator.calculate(
            apparatus = engine,
            template = engineTemplate,
            latestFinalized = inspection,
            draft = null,
            nowMillis = now
        )

        assertEquals(InspectionComplianceStatus.CURRENT, result?.status)
    }

    @Test
    fun completedOutsideInterval_isOverdue_withCorrectDays() {
        val now = 200_000_000L
        val completedAt = now - (50 * 3_600_000L)
        val inspection = finalizedInspection(completedAt)

        val result = InspectionComplianceCalculator.calculate(
            apparatus = engine,
            template = engineTemplate,
            latestFinalized = inspection,
            draft = null,
            nowMillis = now
        )

        assertEquals(InspectionComplianceStatus.OVERDUE, result?.status)
        assertEquals(1, result?.daysOverdue)
    }

    @Test
    fun draftWithoutRecentFinalized_isInProgress() {
        val now = 100_000_000L
        val draft = Inspection(
            id = "draft-1",
            templateId = "tmpl-engine",
            apparatusId = "ap-1",
            departmentId = "dept-1",
            startedAt = now - 3_600_000L,
            startedByUserId = "user-1",
            isFinalized = false
        )

        val result = InspectionComplianceCalculator.calculate(
            apparatus = engine,
            template = engineTemplate,
            latestFinalized = null,
            draft = draft,
            nowMillis = now
        )

        assertEquals(InspectionComplianceStatus.IN_PROGRESS, result?.status)
        assertEquals("draft-1", result?.draftInspectionId)
    }

    @Test
    fun dueSoon_withinThreshold() {
        val now = 100_000_000L
        val completedAt = now - (22 * 3_600_000L)
        val inspection = finalizedInspection(completedAt)

        val result = InspectionComplianceCalculator.calculate(
            apparatus = engine,
            template = engineTemplate,
            latestFinalized = inspection,
            draft = null,
            nowMillis = now,
            dueSoonThresholdHours = 4
        )

        assertEquals(InspectionComplianceStatus.DUE_SOON, result?.status)
    }

    @Test
    fun weeklyTemplate_usesSevenDayInterval() {
        val weeklyTemplate = engineTemplate.copy(frequencyHours = 168)
        val now = 1_000_000_000L
        val completedAt = now - (100 * 3_600_000L)
        val inspection = finalizedInspection(completedAt)

        val result = InspectionComplianceCalculator.calculate(
            apparatus = engine,
            template = weeklyTemplate,
            latestFinalized = inspection,
            draft = null,
            nowMillis = now
        )

        assertEquals(InspectionComplianceStatus.CURRENT, result?.status)
    }

    @Test
    fun reserveApparatus_excluded() {
        val reserveEngine = engine.copy(status = ApparatusStatus.RESERVE)

        val result = InspectionComplianceCalculator.calculate(
            apparatus = reserveEngine,
            template = engineTemplate,
            latestFinalized = null,
            draft = null,
            nowMillis = 1_000_000L
        )

        assertNull(result)
    }

    @Test
    fun calculateForDepartment_emitsStatusPerAssignedTemplate() {
        val weekly = engineTemplate.copy(
            id = "tmpl-weekly",
            name = "Weekly Inventory",
            frequencyHours = 168
        )
        val now = 200_000_000L
        val statuses = InspectionComplianceCalculator.calculateForDepartment(
            apparatusList = listOf(
                engine.copy(assignedTemplateIds = listOf(engineTemplate.id, weekly.id))
            ),
            templates = listOf(engineTemplate, weekly),
            inspections = listOf(
                finalizedInspection(now - (50 * 3_600_000L)).copy(templateId = engineTemplate.id)
            ),
            nowMillis = now
        )

        assertEquals(2, statuses.size)
        assertEquals(
            InspectionComplianceStatus.OVERDUE,
            statuses.first { it.templateId == engineTemplate.id }.status
        )
        assertEquals(
            InspectionComplianceStatus.NEVER_INSPECTED,
            statuses.first { it.templateId == weekly.id }.status
        )
        assertEquals(
            InspectionComplianceStatus.OVERDUE,
            InspectionComplianceCalculator.worstStatus(statuses)?.status
        )
    }

    private fun finalizedInspection(completedAt: Long): Inspection =
        Inspection(
            id = "insp-1",
            templateId = "tmpl-engine",
            apparatusId = "ap-1",
            departmentId = "dept-1",
            startedAt = completedAt,
            completedAt = completedAt,
            startedByUserId = "user-1",
            isFinalized = true
        )
}
