package com.example.firestationops.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firestationops.currentTimeMillis
import com.example.firestationops.domain.catalog.ApparatusCatalogInput
import com.example.firestationops.domain.catalog.HistoricalInspectionCsvImporter
import com.example.firestationops.domain.catalog.StationCatalogInput
import com.example.firestationops.domain.catalog.TemplateCatalogInput
import com.example.firestationops.domain.catalog.TemplateCsvImporter
import com.example.firestationops.domain.catalog.TemplateItemCatalogInput
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import com.example.firestationops.domain.model.Station
import com.example.firestationops.domain.repository.ApparatusRepository
import com.example.firestationops.domain.repository.CatalogAdminRepository
import com.example.firestationops.domain.repository.DepartmentRepository
import com.example.firestationops.domain.repository.InspectionRepository
import com.example.firestationops.domain.sync.SyncCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val vin: String = "",
    val licensePlate: String = "",
    val barcode: String = "",
    val status: ApparatusStatus = ApparatusStatus.IN_SERVICE,
    val assignedTemplateIds: List<String> = emptyList(),
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

data class TemplateCsvImportState(
    val csvText: String = "",
    val fileName: String? = null,
    val preview: TemplateCsvImporter.Preview? = null,
    val error: String? = null,
    /** When true, replace items in the open template editor; otherwise open a new editor. */
    val replaceOpenEditorItems: Boolean = false
)

data class HistoryCsvImportState(
    val csvText: String = "",
    val fileName: String? = null,
    val preview: HistoricalInspectionCsvImporter.Preview? = null,
    val error: String? = null,
    val keepLocalOnly: Boolean = false,
    val isImporting: Boolean = false
)

class CatalogSettingsViewModel(
    private val member: Member,
    private val apparatusRepository: ApparatusRepository,
    private val inspectionRepository: InspectionRepository,
    private val catalogAdminRepository: CatalogAdminRepository,
    private val syncCoordinator: SyncCoordinator,
    private val departmentRepository: DepartmentRepository
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

    private val _templateCsvImport = MutableStateFlow<TemplateCsvImportState?>(null)
    val templateCsvImport: StateFlow<TemplateCsvImportState?> = _templateCsvImport.asStateFlow()

    private val _historyCsvImport = MutableStateFlow<HistoryCsvImportState?>(null)
    val historyCsvImport: StateFlow<HistoryCsvImportState?> = _historyCsvImport.asStateFlow()

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
            vin = apparatus.vin.orEmpty(),
            licensePlate = apparatus.licensePlate.orEmpty(),
            barcode = apparatus.barcode.orEmpty(),
            status = apparatus.status,
            assignedTemplateIds = apparatus.assignedTemplateIds
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

    fun updateApparatusVin(value: String) {
        _apparatusEditor.value = _apparatusEditor.value?.copy(vin = value)
    }

    fun updateApparatusLicensePlate(value: String) {
        _apparatusEditor.value = _apparatusEditor.value?.copy(licensePlate = value)
    }

    fun updateApparatusBarcode(value: String) {
        _apparatusEditor.value = _apparatusEditor.value?.copy(barcode = value)
    }

    fun updateApparatusStatus(value: ApparatusStatus) {
        _apparatusEditor.value = _apparatusEditor.value?.copy(status = value)
    }

    fun toggleApparatusAssignedTemplate(templateId: String) {
        val editor = _apparatusEditor.value ?: return
        val current = editor.assignedTemplateIds.toMutableList()
        if (current.contains(templateId)) {
            current.remove(templateId)
        } else {
            current.add(templateId)
        }
        _apparatusEditor.value = editor.copy(assignedTemplateIds = current)
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
                    status = editor.status,
                    vin = editor.vin,
                    licensePlate = editor.licensePlate,
                    barcode = editor.barcode,
                    assignedTemplateIds = editor.assignedTemplateIds
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
                    description = it.description,
                    category = it.category,
                    isRequired = it.isRequired,
                    requiresNoteOnFail = it.requiresNoteOnFail,
                    expectedQuantity = it.expectedQuantity
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

    fun openTemplateCsvImport(replaceOpenEditorItems: Boolean = false) {
        _templateCsvImport.value = TemplateCsvImportState(
            replaceOpenEditorItems = replaceOpenEditorItems
        )
    }

    fun closeTemplateCsvImport() {
        _templateCsvImport.value = null
    }

    fun updateTemplateCsvText(value: String) {
        _templateCsvImport.value = _templateCsvImport.value?.copy(
            csvText = value,
            preview = null,
            error = null
        )
    }

    fun setTemplateCsvFromFile(content: String, fileName: String?) {
        val current = _templateCsvImport.value ?: TemplateCsvImportState()
        _templateCsvImport.value = current.copy(
            csvText = content,
            fileName = fileName,
            preview = null,
            error = null
        )
        previewTemplateCsv()
    }

    fun previewTemplateCsv() {
        val state = _templateCsvImport.value ?: return
        when (val result = TemplateCsvImporter.parse(state.csvText)) {
            is TemplateCsvImporter.Result.Success -> {
                _templateCsvImport.value = state.copy(preview = result.preview, error = null)
            }
            is TemplateCsvImporter.Result.Failure -> {
                _templateCsvImport.value = state.copy(preview = null, error = result.message)
            }
        }
    }

    fun applyTemplateCsvImport() {
        val state = _templateCsvImport.value ?: return
        val preview = state.preview ?: run {
            previewTemplateCsv()
            _templateCsvImport.value?.preview
        } ?: return

        val items = preview.items.ifEmpty { listOf(TemplateItemCatalogInput(text = "")) }
        val existingEditor = _templateEditor.value
        if (state.replaceOpenEditorItems && existingEditor != null) {
            _templateEditor.value = existingEditor.copy(items = items)
            _actionMessage.value = "Imported ${preview.items.size} checklist items into the template."
        } else {
            _templateEditor.value = TemplateEditorState(
                frequencyHours = if (preview.quantityItemCount > 0) "168" else "24",
                items = items
            )
            _actionMessage.value = "Imported ${preview.summary}. Review name and apparatus type, then save."
        }
        _templateCsvImport.value = null
    }

    fun openHistoryCsvImport() {
        _historyCsvImport.value = HistoryCsvImportState()
    }

    fun closeHistoryCsvImport() {
        _historyCsvImport.value = null
    }

    fun updateHistoryCsvText(value: String) {
        _historyCsvImport.value = _historyCsvImport.value?.copy(
            csvText = value,
            preview = null,
            error = null
        )
    }

    fun setHistoryCsvFromFile(content: String, fileName: String?) {
        val current = _historyCsvImport.value ?: HistoryCsvImportState()
        _historyCsvImport.value = current.copy(
            csvText = content,
            fileName = fileName,
            preview = null,
            error = null
        )
        previewHistoryCsv()
    }

    fun updateHistoryKeepLocalOnly(value: Boolean) {
        _historyCsvImport.value = _historyCsvImport.value?.copy(keepLocalOnly = value)
    }

    fun previewHistoryCsv() {
        val state = _historyCsvImport.value ?: return
        viewModelScope.launch {
            val result = buildHistoryPreview(state.csvText)
            when (result) {
                is HistoricalInspectionCsvImporter.Result.Success -> {
                    _historyCsvImport.value = state.copy(preview = result.preview, error = null)
                }
                is HistoricalInspectionCsvImporter.Result.Failure -> {
                    _historyCsvImport.value = state.copy(preview = null, error = result.message)
                }
            }
        }
    }

    fun applyHistoryCsvImport() {
        val state = _historyCsvImport.value ?: return
        viewModelScope.launch {
            val preview = state.preview ?: run {
                when (val result = buildHistoryPreview(state.csvText)) {
                    is HistoricalInspectionCsvImporter.Result.Success -> result.preview
                    is HistoricalInspectionCsvImporter.Result.Failure -> {
                        _historyCsvImport.value = state.copy(error = result.message, preview = null)
                        return@launch
                    }
                }
            }
            if (preview.importableCount == 0) {
                _historyCsvImport.value = state.copy(
                    preview = preview,
                    error = "No importable inspections in preview."
                )
                return@launch
            }

            _historyCsvImport.value = state.copy(isImporting = true, preview = preview, error = null)
            val inspections = HistoricalInspectionCsvImporter.buildInspections(
                preview = preview,
                importedAt = currentTimeMillis(),
                importedByUserId = member.id,
                keepLocalOnly = state.keepLocalOnly
            )
            var saved = 0
            inspections.forEach { inspection ->
                if (inspectionRepository.saveInspection(inspection).isSuccess) {
                    saved++
                }
            }
            if (!state.keepLocalOnly && cloudSyncEnabled && saved > 0) {
                syncCoordinator.syncDepartment(departmentId)
            }
            _historyCsvImport.value = null
            _actionMessage.value = "Imported $saved historical inspection(s)."
        }
    }

    private suspend fun buildHistoryPreview(csvText: String): HistoricalInspectionCsvImporter.Result {
        val apparatus = apparatusRepository.getApparatusByDepartment(departmentId).first()
        val templates = inspectionRepository.getTemplatesByDepartment(departmentId).first()
        val inspections = inspectionRepository.getInspectionsByDepartment(departmentId).first()
        val members = departmentRepository.getMembersByDepartment(departmentId).getOrDefault(emptyList())
        return HistoricalInspectionCsvImporter.parse(
            csvText = csvText,
            apparatus = apparatus,
            templates = templates,
            members = members,
            existingInspections = inspections,
            departmentId = departmentId
        )
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
