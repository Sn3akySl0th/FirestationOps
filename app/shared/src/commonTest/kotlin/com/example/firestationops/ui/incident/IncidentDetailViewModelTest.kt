package com.example.firestationops.ui.incident

import com.example.firestationops.domain.model.*
import com.example.firestationops.domain.repository.mock.MockIncidentRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentDetailViewModelTest {

    private val member = Member(
        id = "member-1",
        departmentId = "mock-dept-id",
        email = "officer@example.com",
        firstName = "Alex",
        lastName = "Rivera",
        roles = setOf(Role.OFFICER)
    )

    private val incidentRepository = MockIncidentRepository()

    private fun sampleIncident(
        id: String = "inc-1",
        title: String = "Structure fire",
        status: IncidentStatus = IncidentStatus.DRAFT
    ) = Incident(
        id = id,
        departmentId = member.departmentId,
        title = title,
        summary = "Smoke showing",
        locationDescription = "123 Example St",
        incidentType = IncidentType.FIRE,
        status = status,
        createdAt = 1_000L,
        createdByUserId = member.id,
        updatedAt = 1_000L,
        updatedByUserId = member.id
    )

    @Test
    fun load_existingIncident_populatesFields() = runTest {
        val incident = sampleIncident()
        incidentRepository.saveIncident(incident)

        val viewModel = IncidentDetailViewModel(
            incidentId = incident.id,
            member = member,
            incidentRepository = incidentRepository,
            scope = this,
            collectionScope = backgroundScope
        )

        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(incident.id, state.incidentId)
        assertEquals("Structure fire", state.title)
        assertEquals("Smoke showing", state.summary)
        assertEquals(IncidentStatus.DRAFT, state.status)
        assertTrue(state.canEditFields)
        assertTrue(state.canAppendLog)
    }

    @Test
    fun updateTitle_autosavesDraft() = runTest {
        val incident = sampleIncident()
        incidentRepository.saveIncident(incident)

        val viewModel = IncidentDetailViewModel(
            incidentId = incident.id,
            member = member,
            incidentRepository = incidentRepository,
            scope = this,
            collectionScope = backgroundScope
        )
        viewModel.uiState.first { !it.isLoading }

        viewModel.updateTitle("Updated title")
        advanceUntilIdle()

        val saved = incidentRepository.getIncident(incident.id).getOrThrow()
        assertEquals("Updated title", saved.title)
        assertEquals(SyncStatus.PENDING_SYNC, saved.syncStatus)
    }

    @Test
    fun activateIncident_changesStatusToActive() = runTest {
        val incident = sampleIncident()
        incidentRepository.saveIncident(incident)

        val viewModel = IncidentDetailViewModel(
            incidentId = incident.id,
            member = member,
            incidentRepository = incidentRepository,
            scope = this,
            collectionScope = backgroundScope
        )
        viewModel.uiState.first { !it.isLoading }

        viewModel.activateIncident()
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.status == IncidentStatus.ACTIVE }
        assertEquals(IncidentStatus.ACTIVE, state.status)
        assertTrue(state.canAppendLog)

        val saved = incidentRepository.getIncident(incident.id).getOrThrow()
        assertEquals(IncidentStatus.ACTIVE, saved.status)
    }

    @Test
    fun activateIncident_withoutTitle_showsError() = runTest {
        val incident = sampleIncident(title = "")
        incidentRepository.saveIncident(incident)

        val viewModel = IncidentDetailViewModel(
            incidentId = incident.id,
            member = member,
            incidentRepository = incidentRepository,
            scope = this,
            collectionScope = backgroundScope
        )
        viewModel.uiState.first { !it.isLoading }

        viewModel.activateIncident()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(IncidentStatus.DRAFT, state.status)
        assertNotNull(state.error)
    }

    @Test
    fun appendLogEntry_addsTimelineEntry() = runTest {
        val incident = sampleIncident(status = IncidentStatus.ACTIVE)
        incidentRepository.saveIncident(incident)

        val viewModel = IncidentDetailViewModel(
            incidentId = incident.id,
            member = member,
            incidentRepository = incidentRepository,
            scope = this,
            collectionScope = backgroundScope
        )
        viewModel.uiState.first { !it.isLoading }

        viewModel.updateNewLogMessage("First unit on scene")
        viewModel.appendLogEntry()
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.timeline.isNotEmpty() }
        assertEquals(1, state.timeline.size)
        assertEquals("First unit on scene", state.timeline.first().message)
        assertEquals(CommandLogEntryType.LOG, state.timeline.first().entryType)
    }

    @Test
    fun appendCorrection_createsCorrectionEntry() = runTest {
        val incident = sampleIncident(status = IncidentStatus.ACTIVE)
        incidentRepository.saveIncident(incident)

        val viewModel = IncidentDetailViewModel(
            incidentId = incident.id,
            member = member,
            incidentRepository = incidentRepository,
            scope = this,
            collectionScope = backgroundScope
        )
        viewModel.uiState.first { !it.isLoading }

        viewModel.updateNewLogMessage("Original entry")
        viewModel.appendLogEntry()
        advanceUntilIdle()
        val originalEntry = viewModel.uiState.first { it.timeline.isNotEmpty() }.timeline.first()

        viewModel.startCorrection(originalEntry.id)
        viewModel.updateNewLogMessage("Corrected time of arrival")
        viewModel.appendCorrection()
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.timeline.size == 2 }
        val correction = state.timeline.last()
        assertEquals(CommandLogEntryType.CORRECTION, correction.entryType)
        assertEquals(originalEntry.id, correction.correctsEntryId)
        assertEquals("Corrected time of arrival", correction.message)
        assertNull(state.correctingEntryId)
    }

    @Test
    fun closeIncident_disablesEditingAndTimelineAppend() = runTest {
        val incident = sampleIncident(status = IncidentStatus.ACTIVE)
        incidentRepository.saveIncident(incident)

        val viewModel = IncidentDetailViewModel(
            incidentId = incident.id,
            member = member,
            incidentRepository = incidentRepository,
            scope = this,
            collectionScope = backgroundScope
        )
        viewModel.uiState.first { !it.isLoading }

        viewModel.closeIncident()
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.status == IncidentStatus.CLOSED }
        assertFalse(state.canEditFields)
        assertFalse(state.canAppendLog)

        val saved = incidentRepository.getIncident(incident.id).getOrThrow()
        assertEquals(IncidentStatus.CLOSED, saved.status)
        assertNotNull(saved.closedAt)
    }
}
