package com.example.firestationops.domain.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemplateCsvImporterTest {

    @Test
    fun parse_readsEngineStyleRowsWithQuantities() {
        val csv = """
            category,text,description,expectedQuantity,requiresNoteOnFail
            Cab,Command Radio,,1,true
            Cab,Headlights Low/High,Functional check,,true
            O.S. Compartment 2,Boxes of Gloves,,4,true
            Functional Checks,Chainsaw — Does it run?,,,true
        """.trimIndent()

        val result = TemplateCsvImporter.parse(csv)
        val preview = assertIs<TemplateCsvImporter.Result.Success>(result).preview

        assertEquals(4, preview.items.size)
        assertEquals(3, preview.categoryCount)
        assertEquals(2, preview.quantityItemCount)
        assertEquals("Command Radio", preview.items[0].text)
        assertEquals("Cab", preview.items[0].category)
        assertEquals(1, preview.items[0].expectedQuantity)
        assertNull(preview.items[1].expectedQuantity)
        assertEquals(4, preview.items[2].expectedQuantity)
        assertTrue(preview.items[3].requiresNoteOnFail)
    }

    @Test
    fun parse_supportsQuotedCommasAndBooleanAliases() {
        val csv = """
            category,text,expectedQuantity,requiresNoteOnFail
            Tools,"Halligan, pick, and pry",1,yes
            Tools,Spare tips,,0
        """.trimIndent()

        val preview = assertIs<TemplateCsvImporter.Result.Success>(
            TemplateCsvImporter.parse(csv)
        ).preview

        assertEquals("Halligan, pick, and pry", preview.items[0].text)
        assertTrue(preview.items[0].requiresNoteOnFail)
        assertEquals(false, preview.items[1].requiresNoteOnFail)
    }

    @Test
    fun parse_failsWithoutTextColumn() {
        val result = TemplateCsvImporter.parse("category,qty\nCab,1")
        val failure = assertIs<TemplateCsvImporter.Result.Failure>(result)
        assertTrue(failure.message.contains("text", ignoreCase = true))
    }

    @Test
    fun parse_warnsOnInvalidQuantityButKeepsRow() {
        val csv = """
            category,text,expectedQuantity
            Cab,Command Radio,abc
        """.trimIndent()

        val preview = assertIs<TemplateCsvImporter.Result.Success>(
            TemplateCsvImporter.parse(csv)
        ).preview

        assertEquals(1, preview.items.size)
        assertNull(preview.items[0].expectedQuantity)
        assertEquals(1, preview.warnings.size)
    }

    @Test
    fun parse_failsWhenEmpty() {
        assertIs<TemplateCsvImporter.Result.Failure>(TemplateCsvImporter.parse("   "))
    }
}
