package com.example.firestationops.ui.deficiency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.DeficiencyStatus
import com.example.firestationops.domain.repository.ApparatusRepository
import com.example.firestationops.domain.repository.DeficiencyRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DeficiencyDetailViewModel(
    private val deficiencyId: String,
    private val userId: String,
    private val deficiencyRepository: DeficiencyRepository,
    private val apparatusRepository: ApparatusRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<DeficiencyDetailUiState>(DeficiencyDetailUiState.Loading)
    val uiState: StateFlow<DeficiencyDetailUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val result = deficiencyRepository.getDeficiency(deficiencyId)
            result.onSuccess { deficiency ->
                val apparatusResult = apparatusRepository.getApparatus(deficiency.apparatusId)
                _uiState.value = DeficiencyDetailUiState.Success(
                    deficiency = deficiency,
                    apparatus = apparatusResult.getOrNull()
                )
            }.onFailure {
                _uiState.value = DeficiencyDetailUiState.Error(it.message ?: "Failed to load deficiency")
            }
        }
    }

    fun resolve(note: String) {
        if (note.isBlank()) return
        
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is DeficiencyDetailUiState.Success) return@launch

            _uiState.value = currentState.copy(isResolving = true)
            
            val result = deficiencyRepository.resolveDeficiency(deficiencyId, userId, note)
            result.onSuccess {
                // If it was an OOS deficiency, check if we should restore apparatus status
                if (currentState.deficiency.severity == DeficiencySeverity.OUT_OF_SERVICE) {
                    val apparatusId = currentState.deficiency.apparatusId
                    val otherOosDeficiencies = deficiencyRepository.getDeficienciesForApparatus(apparatusId)
                        .first()
                        .filter { it.id != deficiencyId && it.severity == DeficiencySeverity.OUT_OF_SERVICE && (it.status == DeficiencyStatus.OPEN || it.status == DeficiencyStatus.ASSIGNED) }
                    
                    if (otherOosDeficiencies.isEmpty()) {
                        apparatusRepository.updateApparatusStatus(apparatusId, ApparatusStatus.IN_SERVICE)
                    }
                }
                _uiState.value = DeficiencyDetailUiState.Resolved
            }.onFailure { error ->
                _uiState.value = currentState.copy(isResolving = false, errorMessage = error.message)
            }
        }
    }
}

sealed interface DeficiencyDetailUiState {
    object Loading : DeficiencyDetailUiState
    data class Success(
        val deficiency: Deficiency,
        val apparatus: Apparatus?,
        val isResolving: Boolean = false,
        val errorMessage: String? = null
    ) : DeficiencyDetailUiState
    object Resolved : DeficiencyDetailUiState
    data class Error(val message: String) : DeficiencyDetailUiState
}
