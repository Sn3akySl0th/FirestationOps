package com.example.firestationops.ui.department

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firestationops.domain.bootstrap.DepartmentCatalogBootstrap
import com.example.firestationops.domain.membership.MemberProvisioningRules
import com.example.firestationops.domain.membership.MemberRosterInput
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import com.example.firestationops.domain.repository.DepartmentRepository
import com.example.firestationops.domain.repository.MemberRosterRepository
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
        val canManageRoster: Boolean,
        val canBootstrapCatalog: Boolean,
        val cloudCatalogEmpty: Boolean
    ) : DepartmentSettingsUiState

    data class Error(val message: String) : DepartmentSettingsUiState
}

data class MemberEditorState(
    val memberId: String? = null,
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val memberNumber: String = "",
    val initialPassword: String = "",
    val showInitialPasswordField: Boolean = false,
    val roles: Set<Role> = setOf(Role.MEMBER),
    val isActive: Boolean = true,
    val isSaving: Boolean = false
)

class DepartmentSettingsViewModel(
    private val member: Member,
    private val departmentRepository: DepartmentRepository,
    private val memberRosterRepository: MemberRosterRepository,
    private val departmentCatalogBootstrap: DepartmentCatalogBootstrap,
    private val syncCoordinator: SyncCoordinator
) : ViewModel() {
    private val _uiState = MutableStateFlow<DepartmentSettingsUiState>(DepartmentSettingsUiState.Loading)
    val uiState: StateFlow<DepartmentSettingsUiState> = _uiState.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _editorState = MutableStateFlow<MemberEditorState?>(null)
    val editorState: StateFlow<MemberEditorState?> = _editorState.asStateFlow()

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
                canManageRoster = member.hasRole(Role.ADMIN),
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

    fun openNewMemberEditor() {
        _editorState.value = MemberEditorState(showInitialPasswordField = cloudSyncEnabled)
    }

    fun openMemberEditor(existing: Member) {
        _editorState.value = MemberEditorState(
            memberId = existing.id,
            email = existing.email,
            firstName = existing.firstName,
            lastName = existing.lastName,
            memberNumber = existing.memberNumber.orEmpty(),
            roles = existing.roles,
            isActive = existing.isActive
        )
    }

    fun closeMemberEditor() {
        _editorState.value = null
    }

    fun updateEditorEmail(value: String) {
        _editorState.value = _editorState.value?.copy(email = value)
    }

    fun updateEditorFirstName(value: String) {
        _editorState.value = _editorState.value?.copy(firstName = value)
    }

    fun updateEditorLastName(value: String) {
        _editorState.value = _editorState.value?.copy(lastName = value)
    }

    fun updateEditorMemberNumber(value: String) {
        _editorState.value = _editorState.value?.copy(memberNumber = value)
    }

    fun updateEditorInitialPassword(value: String) {
        _editorState.value = _editorState.value?.copy(initialPassword = value)
    }

    fun toggleEditorRole(role: Role) {
        val editor = _editorState.value ?: return
        val roles = if (role in editor.roles) {
            editor.roles - role
        } else {
            editor.roles + role
        }
        _editorState.value = editor.copy(roles = roles)
    }

    fun updateEditorActive(isActive: Boolean) {
        _editorState.value = _editorState.value?.copy(isActive = isActive)
    }

    fun saveMemberEditor() {
        val editor = _editorState.value ?: return
        viewModelScope.launch {
            _editorState.value = editor.copy(isSaving = true)
            val input = MemberRosterInput(
                email = editor.email,
                firstName = editor.firstName,
                lastName = editor.lastName,
                memberNumber = editor.memberNumber.takeIf { it.isNotBlank() },
                roles = editor.roles,
                isActive = editor.isActive,
                initialPassword = editor.initialPassword.takeIf { it.isNotBlank() }
            )

            val result = memberRosterRepository.upsertMember(
                actingMember = member,
                input = input,
                editingMemberId = editor.memberId
            )

            result.onSuccess { savedMember ->
                if (cloudSyncEnabled) {
                    syncCoordinator.syncDepartment(member.departmentId)
                }
                _editorState.value = null
                _actionMessage.value = when {
                    editor.showInitialPasswordField && editor.memberId == null ->
                        "Member added. They can sign in with ${savedMember.email} and the password you set."
                    MemberProvisioningRules.isPendingMemberId(savedMember.id) ->
                        "Member saved to the roster."
                    else ->
                        "Member roster updated."
                }
                refresh()
            }.onFailure { error ->
                _editorState.value = editor.copy(isSaving = false)
                _actionMessage.value = error.message ?: "Unable to save member."
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
