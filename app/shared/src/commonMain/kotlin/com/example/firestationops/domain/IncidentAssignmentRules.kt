package com.example.firestationops.domain

import com.example.firestationops.domain.model.AssignmentStatus
import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.model.IncidentStatus
import com.example.firestationops.domain.model.IncidentUnitAssignment
import com.example.firestationops.domain.model.PersonnelAssignment

object IncidentAssignmentRules {
    fun canManageAssignments(incident: Incident): Boolean =
        incident.status == IncidentStatus.DRAFT || incident.status == IncidentStatus.ACTIVE

    fun validateNewUnitAssignment(
        incident: Incident,
        apparatusId: String,
        existing: List<IncidentUnitAssignment>
    ): String? {
        if (!canManageAssignments(incident)) {
            return "Cannot assign units to a closed incident"
        }
        if (apparatusId.isBlank()) {
            return "Apparatus is required"
        }
        val active = existing.firstOrNull { it.apparatusId == apparatusId && it.status != AssignmentStatus.RELEASED }
        if (active != null) {
            return "Apparatus is already assigned to this incident"
        }
        return null
    }

    fun validateNewPersonnelAssignment(
        incident: Incident,
        memberId: String,
        existing: List<PersonnelAssignment>
    ): String? {
        if (!canManageAssignments(incident)) {
            return "Cannot assign personnel to a closed incident"
        }
        if (memberId.isBlank()) {
            return "Member is required"
        }
        val active = existing.firstOrNull { it.memberId == memberId && it.status != AssignmentStatus.RELEASED }
        if (active != null) {
            return "Member is already assigned to this incident"
        }
        return null
    }

    fun validateStatusTransition(current: AssignmentStatus, next: AssignmentStatus): String? {
        if (current == next) return null
        val allowed = transitions[current].orEmpty()
        return if (next in allowed) {
            null
        } else {
            "Cannot change status from ${formatStatus(current)} to ${formatStatus(next)}"
        }
    }

    fun statusLogMessage(label: String, from: AssignmentStatus, to: AssignmentStatus): String =
        "$label status: ${formatStatus(from)} -> ${formatStatus(to)}"

    fun activeUnitAssignments(assignments: List<IncidentUnitAssignment>): List<IncidentUnitAssignment> =
        assignments.filter { it.status != AssignmentStatus.RELEASED }

    fun activePersonnelAssignments(assignments: List<PersonnelAssignment>): List<PersonnelAssignment> =
        assignments.filter { it.status != AssignmentStatus.RELEASED }

    private val transitions = mapOf(
        AssignmentStatus.ASSIGNED to setOf(AssignmentStatus.EN_ROUTE, AssignmentStatus.RELEASED),
        AssignmentStatus.EN_ROUTE to setOf(AssignmentStatus.ON_SCENE, AssignmentStatus.RELEASED),
        AssignmentStatus.ON_SCENE to setOf(AssignmentStatus.RELEASED),
        AssignmentStatus.RELEASED to setOf(AssignmentStatus.ASSIGNED)
    )

    private fun formatStatus(status: AssignmentStatus): String =
        status.name.replace('_', ' ')
}
