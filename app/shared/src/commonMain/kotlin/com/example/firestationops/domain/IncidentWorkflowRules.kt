package com.example.firestationops.domain

import com.example.firestationops.domain.model.CommandLogEntry
import com.example.firestationops.domain.model.CommandLogEntryType
import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.model.IncidentStatus

object IncidentWorkflowRules {
    fun canEditIncidentFields(incident: Incident): Boolean =
        incident.status == IncidentStatus.DRAFT || incident.status == IncidentStatus.ACTIVE

    fun canAppendLogEntry(incident: Incident): Boolean =
        incident.status == IncidentStatus.DRAFT || incident.status == IncidentStatus.ACTIVE

    fun canActivate(incident: Incident): Boolean =
        incident.status == IncidentStatus.DRAFT && incident.title.isNotBlank()

    fun canClose(incident: Incident): Boolean =
        (incident.status == IncidentStatus.DRAFT || incident.status == IncidentStatus.ACTIVE) &&
            incident.title.isNotBlank()

    fun validateLogEntry(
        incident: Incident,
        message: String,
        entryType: CommandLogEntryType,
        correctsEntryId: String?
    ): String? {
        if (!canAppendLogEntry(incident)) {
            return "Cannot add timeline entries to a closed incident"
        }
        if (message.isBlank()) {
            return "Timeline entry message is required"
        }
        if (entryType == CommandLogEntryType.CORRECTION && correctsEntryId.isNullOrBlank()) {
            return "Correction entries must reference the entry being corrected"
        }
        return null
    }

    fun validateActivation(incident: Incident): String? =
        if (canActivate(incident)) null else "Title is required before opening the incident"

    fun validateClose(incident: Incident): String? =
        if (canClose(incident)) null else "Title is required before closing the incident"

    fun sortTimeline(entries: List<CommandLogEntry>): List<CommandLogEntry> =
        entries.sortedWith(compareBy<CommandLogEntry> { it.incidentTimestamp ?: it.createdAt }.thenBy { it.createdAt })
}
