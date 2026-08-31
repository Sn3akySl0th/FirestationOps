package com.example.firestationops.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firestationops.domain.catalog.ApparatusCatalogInput
import com.example.firestationops.domain.catalog.StationCatalogInput
import com.example.firestationops.domain.catalog.TemplateCatalogInput
import com.example.firestationops.domain.catalog.TemplateItemCatalogInput
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import com.example.firestationops.domain.model.Station
import com.example.firestationops.domain.repository.ApparatusRepository
import com.example.firestationops.domain.repository.CatalogAdminRepository
import com.example.firestationops.domain.repository.InspectionRepository
import com.example.firestationops.domain.sync.SyncCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CatalogSection {
    STATIONS,
    APPARATUS,
    TEMPLATES
}

sealed interface CatalogSettingsUiState {
    data object Loading : CatalogSettingsUiState
    data class Success(
        val canManageCatalog: Boolean,
        val cloudSyncEnabled: Boolean,
        val section: CatalogSection,
        val stations: List<Station>,
        val apparatus: List<Apparatus>,
        val templates: List<InspectionTemplate>
    ) : CatalogSettingsUiState
}

data class StationEditorState(
    val stationId: String? = null,
    val name: String = "",
    val address: String = "",
    val isSaving: Boolean = false
)

data class ApparatusEditorState(
    val apparatusId: String? = null,
    val stationId: String = "",
    val name: String = "",
    val type: String = "",
    val radioName: String = "",
    val status: ApparatusStatus = ApparatusStatus.IN_SERVICE,
    val isSaving: Boolean = false
)

data class TemplateEditorState(
    val templateId: String? = null,
    val name: String = "",
    val description: String = "",
    val apparatusType: String = "",
    val frequencyHours: String = "24",
    val isActive: Boolean = true,
    val items: List<TemplateItemCatalogInput> = listOf(TemplateItemCatalogInput(text = "")),
    val isSaving: Boolean = false
)

class CatalogSettingsViewModel(
    private val member: Member,
    private val apparatusRepository: ApparatusRepository,
    private val inspectionRepository: InspectionRepository,
    private val catalogAdminRepository: CatalogAdminRepository,
    private val syncCoordinator: SyncCoordinator
) : ViewModel() {
    private val departmentId = member.departmentId
    private val cloudSyncEnabled = syncCoordinator.isAvailable()

    private val _section = MutableStateFlow(CatalogSection.STATIONS)
    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _stationEditor = MutableStateFlow<StationEditorState?>(null)
    val stationEditor: StateFlow<StationEditorState?> = _stationEditor.asStateFlow()

    private val _apparatusEditor = MutableStateFlow<ApparatusEditorState?>(null)
    val apparatusEditor: StateFlow<ApparatusEditorState?> = _apparatusEditor.asStateFlow()

    private val _templateEditor = MutableStateFlow<TemplateEditorState?>(null)
    val templateEditor: StateFlow<TemplateEditorState?> = _templateEditor.asStateFlow()

    val uiState: StateFlow<CatalogSettingsUiState> = combine(
        apparatusRepository.getStations(departmentId),
        apparatusRepository.getApparatusByDepartment(departmentId),
        inspectionRepository.getTemplatesByDepartment(departmentId),
        _section
    ) { stations, apparatus, templates, section ->
        CatalogSettingsUiState.Success(
            canManageCatalog = member.hasRole(Role.ADMIN),
            cloudSyncEnabled = cloudSyncEnabled,
            section = section,
            stations = stations.sortedBy { it.name },
            apparatus = apparatus.sortedBy { it.radioName },
            templates = templates.sortedBy { it.name }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CatalogSettingsUiState.Loading
    )

    fun selectSection(section: CatalogSection) {
        _section.value = section
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun openNewStationEditor() {
        _stationEditor.value = StationEditorState()
    }

    fun openStationEditor(station: Station) {
        _stationEditor.value = StationEditorState(
            stationId = station.id,
            name = station.name,
            address = station.address.orEmpty()
        )
    }

    fun closeStationEditor() {
        _stationEditor.value = null
    }

    fun updateStationName(value: String) {
        _stationEditor.value = _stationEditor.value?.copy(name = value)
    }

    fun updateStationAddress(value: String) {
        _stationEditor.value = _stationEditor.value?.copy(address = value)
    }

    fun saveStationEditor() {
        val editor = _stationEditor.value ?: return
        viewModelScope.launch {
            _stationEditor.value = editor.copy(isSaving = true)
            val result = catalogAdminRepository.upsertStation(
                actingMember = member,
                input = StationCatalogInput(
                    name = editor.name,
                    address = editor.address.takeIf { it.isNotBlank() }
                ),
                editingStationId = editor.stationId
            )
            result.onSuccess {
                if (cloudSyncEnabled) {
                    syncCoordinator.syncDepartment(departmentId)
                }
                _stationEditor.value = null
                _actionMessage.value = if (editor.stationId == null) "Station added." else "Station updated."
            }.onFailure { error ->
                _stationEditor.value = editor.copy(isSaving = false)
                _actionMessage.value = error.message ?: "Unable to save station."
            }
        }
    }

    fun openNewApparatusEditor(stations: List<Station>) {
        _apparatusEditor.value = ApparatusEditorState(
            stationId = stations.firstOrNull()?.id.orEmpty()
        )
    }

    fun openApparatusEditor(apparatus: Apparatus) {
        _apparatusEditor.value = ApparatusEditorState(
            apparatusId = apparatus.id,
            stationId = apparatus.stationId,
            name = apparatus.name,
            type = apparatus.type,
            radioName = apparatus.radioName,
            status = apparatus.status
        )
    }

    fun closeApparatusEditor() {
        _apparatusEditor.value = null
    }

    fun updateApparatusStationId(value: String) {
        _apparatusEditor.value = _apparatusEditor.value?.copy(stationId = value)
    }

    fun updateApparatusName(value: String) {
        _apparatusEditor.value = _apparatusEditor.value?.copy(name = value)
    }

    fun updateApparatusType(value: String) {
        _apparatusEditor.value = _apparatusEditor.value?.copy(type = value)
    }

    fun updateApparatusRadioName(value: String) {
        _apparatusEditor.value = _apparatusEditor.value?.copy(radioName = value)
    }

    fun updateApparatusStatus(value: ApparatusStatus) {
        _apparatusEditor.value = _apparatusEditor.value?.copy(status = value)
    }

    fun saveApparatusEditor() {
        val editor = _apparatusEditor.value ?: return
        viewModelScope.launch {
            _apparatusEditor.value = editor.copy(isSaving = true)
            val result = catalogAdminRepository.upsertApparatus(
                actingMember = member,
                input = ApparatusCatalogInput(
                    stationId = editor.stationId,
                    name = editor.name,
                    type = editor.type,
                    radioName = editor.radioName,
                    status = editor.status
                ),
                editingApparatusId = editor.apparatusId
            )
            result.onSuccess {
                if (cloudSyncEnabled) {
                    syncCoordinator.syncDepartment(departmentId)
                }
                _apparatusEditor.value = null
                _actionMessage.value = if (editor.apparatusId == null) "Apparatus added." else "Apparatus updated."
            }.onFailure { error ->
                _apparatusEditor.value = editor.copy(isSaving = false)
                _actionMessage.value = error.message ?: "Unable to save apparatus."
            }
        }
    }

    fun openNewTemplateEditor() {
        _templateEditor.value = TemplateEditorState()
    }

    fun openTemplateEditor(template: InspectionTemplate) {
        _templateEditor.value = TemplateEditorState(
            templateId = template.id,
            name = template.name,
            description = template.description.orEmpty(),
            apparatusType = template.apparatusType,
            frequencyHours = template.frequencyHours.toString(),
            isActive = template.isActive,
            items = template.items.map {
                TemplateItemCatalogInput(
                    id = it.id,
                    text = it.text,
                    category = it.category,
                    isRequired = it.isRequired,
                    requiresNoteOnFail = it.requiresNoteOnFail
                )
            }.ifEmpty { listOf(TemplateItemCatalogInput(text = "")) }
        )
    }

    fun closeTemplateEditor() {
        _templateEditor.value = null
    }

    fun updateTemplateName(value: String) {
        _templateEditor.value = _templateEditor.value?.copy(name = value)
    }

    fun updateTemplateDescription(value: String) {
        _templateEditor.value = _templateEditor.value?.copy(description = value)
    }

    fun updateTemplateApparatusType(value: String) {
        _templateEditor.value = _templateEditor.value?.copy(apparatusType = value)
    }

    fun updateTemplateFrequencyHours(value: String) {
        _templateEditor.value = _templateEditor.value?.copy(frequencyHours = value)
    }

    fun updateTemplateActive(isActive: Boolean) {
        _templateEditor.value = _templateEditor.value?.copy(isActive = isActive)
    }

    fun updateTemplateItemText(index: Int, value: String) {
        val editor = _templateEditor.value ?: return
        val items = editor.items.toMutableList()
        if (index in items.indices) {
            items[index] = items[index].copy(text = value)
            _templateEditor.value = editor.copy(items = items)
        }
    }

    fun addTemplateItem() {
        val editor = _templateEditor.value ?: return
        _templateEditor.value = editor.copy(
            items = editor.items + TemplateItemCatalogInput(text = "")
        )
    }

    fun removeTemplateItem(index: Int) {
        val editor = _templateEditor.value ?: return
        if (editor.items.size <= 1) return
        _templateEditor.value = editor.copy(items = editor.items.filterIndexed { i, _ -> i != index })
    }

    fun saveTemplateEditor() {
        val editor = _templateEditor.value ?: return
        viewModelScope.launch {
            _templateEditor.value = editor.copy(isSaving = true)
            val frequency = editor.frequencyHours.toIntOrNull() ?: 24
            val result = catalogAdminRepository.upsertTemplate(
                actingMember = member,
                input = TemplateCatalogInput(
                    name = editor.name,
                    description = editor.description.takeIf { it.isNotBlank() },
                    apparatusType = editor.apparatusType,
                    frequencyHours = frequency,
                    isActive = editor.isActive,
                    items = editor.items
                ),
                editingTemplateId = editor.templateId
            )
            result.onSuccess {
                if (cloudSyncEnabled) {
                    syncCoordinator.syncDepartment(departmentId)
                }
                _templateEditor.value = null
                _actionMessage.value = if (editor.templateId == null) "Template added." else "Template updated."
            }.onFailure { error ->
                _templateEditor.value = editor.copy(isSaving = false)
                _actionMessage.value = error.message ?: "Unable to save template."
            }
        }
    }
}
