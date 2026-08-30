package com.example.firestationops.ui.deficiency

import com.example.firestationops.domain.model.*
import com.example.firestationops.domain.repository.mock.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeficiencyDetailViewModelTest {

    private val deficiencyRepository = MockDeficiencyRepository()
    private val apparatusRepository = MockApparatusRepository()
    
    private val mockDeficiency = Deficiency(
        id = "def-1",
        apparatusId = "ap-1",
        departmentId = "mock-dept-id",
        title = "Oil Leak",
        description = "Heavy leak under engine",
        severity = DeficiencySeverity.OUT_OF_SERVICE,
        status = DeficiencyStatus.OPEN,
        createdAt = 1000L,
        createdByUserId = "user-1"
    )

    @Test
    fun `loadData should load deficiency and apparatus`() = runTest {
        deficiencyRepository.saveDeficiency(mockDeficiency)
        
        val viewModel = DeficiencyDetailViewModel(
            deficiencyId = "def-1",
            userId = "user-1",
            deficiencyRepository = deficiencyRepository,
            apparatusRepository = apparatusRepository
        )

        viewModel.uiState.first { it is DeficiencyDetailUiState.Success }
        
        val state = viewModel.uiState.value as DeficiencyDetailUiState.Success
        assertEquals("Oil Leak", state.deficiency.title)
        assertEquals("E1", state.apparatus?.radioName)
    }

    @Test
    fun `resolve should update deficiency status`() = runTest {
        deficiencyRepository.saveDeficiency(mockDeficiency)
        
        val viewModel = DeficiencyDetailViewModel(
            deficiencyId = "def-1",
            userId = "user-1",
            deficiencyRepository = deficiencyRepository,
            apparatusRepository = apparatusRepository
        )

        viewModel.uiState.first { it is DeficiencyDetailUiState.Success }
        
        viewModel.resolve("Fixed the gasket")
        
        viewModel.uiState.first { it is DeficiencyDetailUiState.Resolved }
        
        val result = deficiencyRepository.getDeficiency("def-1")
        assertTrue(result.isSuccess)
        assertEquals(DeficiencyStatus.RESOLVED, result.getOrThrow().status)
        assertEquals("Fixed the gasket", result.getOrThrow().resolutionNote)
    }

    @Test
    fun `resolving last OOS deficiency should restore apparatus status`() = runTest {
        // Set apparatus to OOS
        apparatusRepository.updateApparatusStatus("ap-1", ApparatusStatus.OUT_OF_SERVICE)
        deficiencyRepository.saveDeficiency(mockDeficiency)
        
        val viewModel = DeficiencyDetailViewModel(
            deficiencyId = "def-1",
            userId = "user-1",
            deficiencyRepository = deficiencyRepository,
            apparatusRepository = apparatusRepository
        )

        viewModel.uiState.first { it is DeficiencyDetailUiState.Success }
        
        viewModel.resolve("Fixed")
        
        viewModel.uiState.first { it is DeficiencyDetailUiState.Resolved }
        
        val apparatus = apparatusRepository.getApparatus("ap-1").getOrNull()
        assertEquals(ApparatusStatus.IN_SERVICE, apparatus?.status)
    }

    @Test
    fun `resolving OOS deficiency should NOT restore status if other OOS exist`() = runTest {
        // Set apparatus to OOS
        apparatusRepository.updateApparatusStatus("ap-1", ApparatusStatus.OUT_OF_SERVICE)
        deficiencyRepository.saveDeficiency(mockDeficiency)
        deficiencyRepository.saveDeficiency(mockDeficiency.copy(id = "def-2", title = "Another OOS"))
        
        val viewModel = DeficiencyDetailViewModel(
            deficiencyId = "def-1",
            userId = "user-1",
            deficiencyRepository = deficiencyRepository,
            apparatusRepository = apparatusRepository
        )

        viewModel.uiState.first { it is DeficiencyDetailUiState.Success }
        
        viewModel.resolve("Fixed one")
        
        viewModel.uiState.first { it is DeficiencyDetailUiState.Resolved }
        
        val apparatus = apparatusRepository.getApparatus("ap-1").getOrNull()
        assertEquals(ApparatusStatus.OUT_OF_SERVICE, apparatus?.status)
    }
}
