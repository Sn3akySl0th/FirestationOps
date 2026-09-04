package com.example.firestationops.domain.catalog

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.InspectionEntrySource
import com.example.firestationops.domain.model.InspectionStatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.InspectionTemplateItem
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import com.example.firestationops.domain.model.SyncStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HistoricalInspectionCsvImporterTest {

    private val member = Member(
        id = "member-1",
        departmentId = "dept-1",
        email = "alex@example.com",
        firstName = "Alex",
        lastName = "Example",
        memberNumber = "518",
        roles = setOf(Role.MEMBER)
    )

    private val apparatus = Apparatus(
        id = "ap-1",
        departmentId = "dept-1",
        stationId = "st-1",
        name = "Engine 5",
        type = "Engine",
        radioName = "ENGINE 5"
    )

    private val template = InspectionTemplate(
        id = "tmpl-weekly",
        departmentId = "dept-1",
        name = "Weekly Inventory Checkoff — Engine 5",
        apparatusType = "Engine",
        frequencyHours = 168,
        items = listOf(
            InspectionTemplateItem(
                id = "gloves",
                text = "Boxes of Gloves",
                category = "O.S. Compartment 2",
                expectedQuantity = 4
            ),
            InspectionTemplateItem(
                id = "aed",
                text = "AED",
                category = "O.S. Compartment 2",
                expectedQuantity = 1
            )
        )
    )

    @Test
    fun parse_groupsRowsAndMatchesCatalog() {
        val csv = """
            importGroupKey,apparatusRadioName,templateName,completedAt,completedByEmail,category,itemText,status,actualQuantity,note
            2026-06-15-E5,ENGINE 5,Weekly Inventory Checkoff — Engine 5,2026-06-15T08:30:00,alex@example.com,O.S. Compartment 2,Boxes of Gloves,PASS,4,
            2026-06-15-E5,ENGINE 5,Weekly Inventory Checkoff — Engine 5,2026-06-15T08:30:00,alex@example.com,O.S. Compartment 2,AED,FAIL,0,Missing on truck
        """.trimIndent()

        val preview = assertIs<HistoricalInspectionCsvImporter.Result.Success>(
            HistoricalInspectionCsvImporter.parse(
                csvText = csv,
                apparatus = listOf(apparatus),
                templates = listOf(template),
                members = listOf(member),
                existingInspections = emptyList(),
                departmentId = "dept-1"
            )
        ).preview

        assertEquals(1, preview.inspections.size)
        assertTrue(preview.inspections.first().canImport)
        assertEquals(2, preview.inspections.first().items.size)
        assertEquals(InspectionStatus.FAIL, preview.inspections.first().items[1].status)
    }

    @Test
    fun parse_blocksUnmatchedApparatusAndDuplicateDay() {
        val csv = """
            importGroupKey,apparatusRadioName,templateName,completedAt,completedByEmail,category,itemText,status,actualQuantity,note
            g1,UNKNOWN,Weekly Inventory Checkoff — Engine 5,2026-06-15,alex@example.com,O.S. Compartment 2,Boxes of Gloves,PASS,4,
            g2,ENGINE 5,Weekly Inventory Checkoff — Engine 5,2026-06-15,alex@example.com,O.S. Compartment 2,Boxes of Gloves,PASS,4,
            g2,ENGINE 5,Weekly Inventory Checkoff — Engine 5,2026-06-15,alex@example.com,O.S. Compartment 2,AED,PASS,1,
        """.trimIndent()

        val existing = Inspection(
            id = "existing",
            templateId = template.id,
            apparatusId = apparatus.id,
            departmentId = "dept-1",
            startedAt = 1L,
            completedAt = LocalDate(2026, 6, 15)
                .atStartOfDayIn(TimeZone.currentSystemDefault())
                .toEpochMilliseconds(),
            startedByUserId = member.id,
            isFinalized = true
        )

        val preview = assertIs<HistoricalInspectionCsvImporter.Result.Success>(
            HistoricalInspectionCsvImporter.parse(
                csvText = csv,
                apparatus = listOf(apparatus),
                templates = listOf(template),
                members = listOf(member),
                existingInspections = listOf(existing),
                departmentId = "dept-1"
            )
        ).preview

        assertEquals(2, preview.inspections.size)
        assertFalse(preview.inspections[0].canImport)
        assertTrue(preview.inspections[0].errors.any { it.contains("Apparatus") })
        assertFalse(preview.inspections[1].canImport)
        assertTrue(preview.inspections[1].duplicate)
    }

    @Test
    fun buildInspections_marksHistoricalImport() {
        val csv = """
            importGroupKey,apparatusRadioName,templateName,completedAt,completedByEmail,category,itemText,status,actualQuantity,note
            g1,ENGINE 5,Weekly Inventory Checkoff — Engine 5,2026-06-15T09:00:00,alex@example.com,O.S. Compartment 2,Boxes of Gloves,PASS,4,
            g1,ENGINE 5,Weekly Inventory Checkoff — Engine 5,2026-06-15T09:00:00,alex@example.com,O.S. Compartment 2,AED,PASS,1,
        """.trimIndent()

        val preview = assertIs<HistoricalInspectionCsvImporter.Result.Success>(
            HistoricalInspectionCsvImporter.parse(
                csvText = csv,
                apparatus = listOf(apparatus),
                templates = listOf(template),
                members = listOf(member),
                existingInspections = emptyList(),
                departmentId = "dept-1"
            )
        ).preview

        val inspections = HistoricalInspectionCsvImporter.buildInspections(
            preview = preview,
            importedAt = 99L,
            importedByUserId = "admin-1",
            keepLocalOnly = true
        )
        assertEquals(1, inspections.size)
        assertEquals(InspectionEntrySource.HISTORICAL_IMPORT, inspections.first().entrySource)
        assertEquals(SyncStatus.LOCAL_ONLY, inspections.first().syncStatus)
        assertTrue(inspections.first().isFinalized)
        assertEquals(2, inspections.first().responses.size)
        assertEquals(99L, inspections.first().importedAt)
    }
}
