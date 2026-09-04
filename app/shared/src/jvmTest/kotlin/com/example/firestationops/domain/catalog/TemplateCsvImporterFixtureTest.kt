package com.example.firestationops.domain.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TemplateCsvImporterFixtureTest {

    @Test
    fun parse_engine5InventoryCheckoffFixture() {
        val csv = javaClass.classLoader
            .getResourceAsStream("fixtures/inspections/engine-5-inventory-checkoff-template.csv")
            ?.bufferedReader()
            ?.readText()
            ?: error("Missing Engine 5 fixture on test classpath")

        val preview = assertIs<TemplateCsvImporter.Result.Success>(
            TemplateCsvImporter.parse(csv)
        ).preview

        assertEquals(158, preview.items.size)
        assertTrue(preview.categoryCount >= 15)
        assertTrue(preview.quantityItemCount > 100)
        assertTrue(preview.items.any { it.text == "Boxes of Gloves" && it.expectedQuantity == 4 })
        assertTrue(preview.items.any { it.category == "Functional Checks" && it.expectedQuantity == null })
        assertTrue(preview.summary.contains("158"))
    }
}
