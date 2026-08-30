package com.example.firestationops.domain

import com.example.firestationops.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IncidentAssignmentRulesTest {
    private fun openIncident() = Incident(
        id = "inc-1",
        departmentId = "dept-1",
        title = "Structure fire",
        status = IncidentStatus.ACTIVE,
        createdAt = 1_000L,
        createdByUserId = "user-1",
        updatedAt = 1_000L,
        updatedByUserId = "user-1"
    )

    private fun unitAssignment(apparatusId: String, status: AssignmentStatus = AssignmentStatus.ASSIGNED) =
        IncidentUnitAssignment(
            id = "unit-$apparatusId",
            incidentId = "inc-1",
            departmentId = "dept-1",
            apparatusId = apparatusId,
            status = status,
            assignedAt = 1_000L,
            assignedByUserId = "user-1",
            updatedAt = 1_000L,
            updatedByUserId = "user-1"
        )

    @Test
    fun canManageAssignments_allowsDraftAndActive() {
        assertTrue(IncidentAssignmentRules.canManageAssignments(openIncident()))
        assertTrue(
            IncidentAssignmentRules.canManageAssignments(
                openIncident().copy(status = IncidentStatus.DRAFT)
            )
        )
        assertTrue(
            !IncidentAssignmentRules.canManageAssignments(
                openIncident().copy(status = IncidentStatus.CLOSED)
            )
        )
    }

    @Test
    fun validateNewUnitAssignment_rejectsDuplicateActiveAssignment() {
        val error = IncidentAssignmentRules.validateNewUnitAssignment(
            incident = openIncident(),
            apparatusId = "ap-1",
            existing = listOf(unitAssignment("ap-1", AssignmentStatus.ON_SCENE))
        )
        assertNotNull(error)
    }

    @Test
    fun validateNewUnitAssignment_allowsReassignAfterReleased() {
        val error = IncidentAssignmentRules.validateNewUnitAssignment(
            incident = openIncident(),
            apparatusId = "ap-1",
            existing = listOf(unitAssignment("ap-1", AssignmentStatus.RELEASED))
        )
        assertNull(error)
    }

    @Test
    fun validateStatusTransition_enforcesAllowedPaths() {
        assertNull(
            IncidentAssignmentRules.validateStatusTransition(
                AssignmentStatus.ASSIGNED,
                AssignmentStatus.EN_ROUTE
            )
        )
        assertNotNull(
            IncidentAssignmentRules.validateStatusTransition(
                AssignmentStatus.ASSIGNED,
                AssignmentStatus.ON_SCENE
            )
        )
        assertNull(
            IncidentAssignmentRules.validateStatusTransition(
                AssignmentStatus.RELEASED,
                AssignmentStatus.ASSIGNED
            )
        )
    }

    @Test
    fun activeUnitAssignments_excludesReleased() {
        val active = IncidentAssignmentRules.activeUnitAssignments(
            listOf(
                unitAssignment("ap-1", AssignmentStatus.ON_SCENE),
                unitAssignment("ap-2", AssignmentStatus.RELEASED)
            )
        )
        assertEquals(1, active.size)
        assertEquals("ap-1", active.first().apparatusId)
    }
}
