package com.example.firestationops.ui.incident

import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.model.IncidentStatus
import com.example.firestationops.domain.model.IncidentType
import com.example.firestationops.domain.repository.mock.MockIncidentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentListViewModelTest {

    private val departmentId = "mock-dept-id"
    private val incidentRepository = MockIncidentRepository()

    private fun sampleIncident(
        id: String,
        title: String,
        updatedAt: Long
    ) = Incident(
        id = id,
        departmentId = departmentId,
        title = title,
        incidentType = IncidentType.FIRE,
        status = IncidentStatus.DRAFT,
        createdAt = updatedAt,
        createdByUserId = "member-1",
        updatedAt = updatedAt,
        updatedByUserId = "member-1"
    )

    @Test
    fun loadsIncidentsForDepartment() = runTest {
        incidentRepository.saveIncident(sampleIncident("inc-1", "Alpha", 2_000L))
        incidentRepository.saveIncident(sampleIncident("inc-2", "Bravo", 3_000L))
        incidentRepository.saveIncident(
            sampleIncident("inc-other", "Other dept", 4_000L).copy(departmentId = "other-dept")
        )

        val viewModel = IncidentListViewModel(
            departmentId = departmentId,
            incidentRepository = incidentRepository,
            testScope = backgroundScope
        )

        val state = viewModel.uiState.first { it is IncidentListUiState.Success } as IncidentListUiState.Success
        assertEquals(2, state.incidents.size)
        assertEquals("inc-2", state.incidents.first().id)
        assertEquals("inc-1", state.incidents.last().id)
    }

    @Test
    fun showsEmptyWhenNoIncidents() = runTest {
        val viewModel = IncidentListViewModel(
            departmentId = departmentId,
            incidentRepository = incidentRepository,
            testScope = backgroundScope
        )

        val state = viewModel.uiState.first { it is IncidentListUiState.Empty }
        assertTrue(state is IncidentListUiState.Empty)
    }
}
