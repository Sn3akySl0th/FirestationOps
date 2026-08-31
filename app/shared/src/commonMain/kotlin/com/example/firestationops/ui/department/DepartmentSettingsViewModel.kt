package com.example.firestationops.ui.department

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firestationops.domain.bootstrap.DepartmentCatalogBootstrap
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import com.example.firestationops.domain.repository.DepartmentRepository
import com.example.firestationops.domain.sync.SyncCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DepartmentSettingsUiState {
    data object Loading : DepartmentSettingsUiState
    data class Success(
        val departmentName: String,
        val departmentId: String,
        val members: List<Member>,
        val cloudSyncEnabled: Boolean,
        val canBootstrapCatalog: Boolean,
        val cloudCatalogEmpty: Boolean
    ) : DepartmentSettingsUiState

    data class Error(val message: String) : DepartmentSettingsUiState
}

class DepartmentSettingsViewModel(
    private val member: Member,
    private val departmentRepository: DepartmentRepository,
    private val departmentCatalogBootstrap: DepartmentCatalogBootstrap,
    private val syncCoordinator: SyncCoordinator
) : ViewModel() {
    private val _uiState = MutableStateFlow<DepartmentSettingsUiState>(DepartmentSettingsUiState.Loading)
    val uiState: StateFlow<DepartmentSettingsUiState> = _uiState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val cloudSyncEnabled = syncCoordinator.isAvailable()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = DepartmentSettingsUiState.Loading
            val departmentResult = departmentRepository.getDepartment(member.departmentId)
            val membersResult = departmentRepository.getMembersByDepartment(member.departmentId)
            val cloudCatalogEmpty = if (cloudSyncEnabled) {
                departmentCatalogBootstrap.isCloudCatalogEmpty(member.departmentId)
            } else {
                false
            }

            if (departmentResult.isFailure) {
                _uiState.value = DepartmentSettingsUiState.Error(
                    departmentResult.exceptionOrNull()?.message ?: "Unable to load department."
                )
                return@launch
            }

            val department = departmentResult.getOrThrow()
            val members = membersResult.getOrElse { emptyList() }
            _uiState.value = DepartmentSettingsUiState.Success(
                departmentName = department.name,
                departmentId = department.id,
                members = members.sortedBy { it.lastName },
                cloudSyncEnabled = cloudSyncEnabled,
                canBootstrapCatalog = member.hasRole(Role.ADMIN),
                cloudCatalogEmpty = cloudCatalogEmpty
            )
        }
    }

    fun bootstrapCatalog() {
        viewModelScope.launch {
            val uploadedCount = departmentCatalogBootstrap
                .bootstrapDemoCatalog(member.departmentId, member)
                .getOrElse { error ->
                    _actionMessage.value = error.message ?: "Catalog bootstrap failed."
                    return@launch
                }

            syncCoordinator.syncDepartment(member.departmentId)
            _actionMessage.value = "Uploaded $uploadedCount catalog records to the cloud."
            refresh()
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
