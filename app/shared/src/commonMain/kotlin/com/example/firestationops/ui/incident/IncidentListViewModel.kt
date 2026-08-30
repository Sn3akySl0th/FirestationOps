package com.example.firestationops.ui.incident

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.repository.IncidentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class IncidentListViewModel(
    private val departmentId: String,
    private val incidentRepository: IncidentRepository,
    testScope: CoroutineScope? = null
) : ViewModel() {

    private val coroutineScope = testScope ?: viewModelScope

    private val _uiState = MutableStateFlow<IncidentListUiState>(IncidentListUiState.Loading)
    val uiState: StateFlow<IncidentListUiState> = _uiState.asStateFlow()

    init {
        incidentRepository.getIncidentsByDepartment(departmentId)
            .onEach { incidents ->
                _uiState.value = if (incidents.isEmpty()) {
                    IncidentListUiState.Empty
                } else {
                    IncidentListUiState.Success(incidents)
                }
            }
            .catch { _uiState.value = IncidentListUiState.Error(it.message ?: "Failed to load incidents") }
            .launchIn(coroutineScope)
    }
}

sealed interface IncidentListUiState {
    object Loading : IncidentListUiState
    object Empty : IncidentListUiState
    data class Success(val incidents: List<Incident>) : IncidentListUiState
    data class Error(val message: String) : IncidentListUiState
}
