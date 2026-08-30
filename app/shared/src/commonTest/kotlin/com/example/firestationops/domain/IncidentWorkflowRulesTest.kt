package com.example.firestationops.domain

import com.example.firestationops.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IncidentWorkflowRulesTest {
    private fun draftIncident(title: String = "Structure fire") = Incident(
        id = "inc-1",
        departmentId = "mock-dept-id",
        title = title,
        createdAt = 1_000L,
        createdByUserId = "user-1",
        updatedAt = 1_000L,
        updatedByUserId = "user-1"
    )

    @Test
    fun canEditIncidentFields_allowsDraftAndActive() {
        assertTrue(IncidentWorkflowRules.canEditIncidentFields(draftIncident()))
        assertTrue(
            IncidentWorkflowRules.canEditIncidentFields(
                draftIncident().copy(status = IncidentStatus.ACTIVE)
            )
        )
        assertFalse(
            IncidentWorkflowRules.canEditIncidentFields(
                draftIncident().copy(status = IncidentStatus.CLOSED)
            )
        )
    }

    @Test
    fun validateActivation_requiresTitle() {
        assertNull(IncidentWorkflowRules.validateActivation(draftIncident()))
        assertEquals(
            "Title is required before opening the incident",
            IncidentWorkflowRules.validateActivation(draftIncident(title = ""))
        )
        assertEquals(
            "Title is required before opening the incident",
            IncidentWorkflowRules.validateActivation(draftIncident().copy(status = IncidentStatus.ACTIVE))
        )
    }

    @Test
    fun validateLogEntry_requiresMessageAndOpenIncident() {
        val incident = draftIncident().copy(status = IncidentStatus.ACTIVE)
        assertNull(
            IncidentWorkflowRules.validateLogEntry(
                incident = incident,
                message = "Command established",
                entryType = CommandLogEntryType.LOG,
                correctsEntryId = null
            )
        )
        assertEquals(
            "Timeline entry message is required",
            IncidentWorkflowRules.validateLogEntry(
                incident = incident,
                message = "  ",
                entryType = CommandLogEntryType.LOG,
                correctsEntryId = null
            )
        )
        assertEquals(
            "Cannot add timeline entries to a closed incident",
            IncidentWorkflowRules.validateLogEntry(
                incident = incident.copy(status = IncidentStatus.CLOSED),
                message = "Too late",
                entryType = CommandLogEntryType.LOG,
                correctsEntryId = null
            )
        )
        assertEquals(
            "Correction entries must reference the entry being corrected",
            IncidentWorkflowRules.validateLogEntry(
                incident = incident,
                message = "Corrected time",
                entryType = CommandLogEntryType.CORRECTION,
                correctsEntryId = null
            )
        )
    }

    @Test
    fun sortTimeline_usesIncidentTimestampThenCreatedAt() {
        val entries = listOf(
            CommandLogEntry(
                id = "b",
                incidentId = "inc-1",
                departmentId = "mock-dept-id",
                message = "Second",
                createdAt = 3_000L,
                createdByUserId = "user-1",
                incidentTimestamp = 2_500L
            ),
            CommandLogEntry(
                id = "a",
                incidentId = "inc-1",
                departmentId = "mock-dept-id",
                message = "First",
                createdAt = 2_000L,
                createdByUserId = "user-1",
                incidentTimestamp = 1_000L
            ),
            CommandLogEntry(
                id = "c",
                incidentId = "inc-1",
                departmentId = "mock-dept-id",
                message = "Third",
                createdAt = 4_000L,
                createdByUserId = "user-1"
            )
        )

        val sorted = IncidentWorkflowRules.sortTimeline(entries)

        assertEquals(listOf("a", "b", "c"), sorted.map { it.id })
    }
}
