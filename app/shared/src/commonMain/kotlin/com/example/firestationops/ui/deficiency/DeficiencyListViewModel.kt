package com.example.firestationops.ui.deficiency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.repository.ApparatusRepository
import com.example.firestationops.domain.repository.DeficiencyRepository
import kotlinx.coroutines.flow.*

class DeficiencyListViewModel(
    private val departmentId: String,
    private val deficiencyRepository: DeficiencyRepository,
    private val apparatusRepository: ApparatusRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DeficiencyListUiState>(DeficiencyListUiState.Loading)
    val uiState: StateFlow<DeficiencyListUiState> = _uiState.asStateFlow()

    init {
        combine(
            deficiencyRepository.getOpenDeficiencies(departmentId),
            apparatusRepository.getApparatusByDepartment(departmentId)
        ) { deficiencies, apparatusList ->
            if (deficiencies.isEmpty()) {
                DeficiencyListUiState.Empty
            } else {
                val apparatusMap = apparatusList.associateBy { it.id }
                DeficiencyListUiState.Success(
                    deficiencies.map { def ->
                        DeficiencyWithApparatus(def, apparatusMap[def.apparatusId])
                    }.sortedByDescending { it.deficiency.createdAt }
                )
            }
        }
        .onEach { _uiState.value = it }
        .catch { _uiState.value = DeficiencyListUiState.Error(it.message ?: "Unknown error") }
        .launchIn(viewModelScope)
    }
}

data class DeficiencyWithApparatus(
    val deficiency: Deficiency,
    val apparatus: Apparatus?
)

sealed interface DeficiencyListUiState {
    object Loading : DeficiencyListUiState
    object Empty : DeficiencyListUiState
    data class Success(val deficiencies: List<DeficiencyWithApparatus>) : DeficiencyListUiState
    data class Error(val message: String) : DeficiencyListUiState
}
