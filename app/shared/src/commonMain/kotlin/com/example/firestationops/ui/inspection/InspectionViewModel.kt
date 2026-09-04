package com.example.firestationops.ui.inspection

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.randomUUID
import com.example.firestationops.domain.InspectionChecklistSections
import com.example.firestationops.domain.InspectionValidationRules
import com.example.firestationops.domain.TemplateAssignmentRules
import com.example.firestationops.domain.export.InspectionCsvExporter
import com.example.firestationops.domain.export.InspectionPdfExporter
import com.example.firestationops.domain.export.InspectionReport
import com.example.firestationops.domain.export.InspectionReportBuilder
import com.example.firestationops.data.sync.SyncAttachmentCache
import com.example.firestationops.domain.sync.SyncCoordinator
import com.example.firestationops.domain.sync.SyncStatusTransitions
import com.example.firestationops.platform.ExportResult
import com.example.firestationops.platform.FileExporter
import com.example.firestationops.domain.model.*
import com.example.firestationops.domain.repository.DeficiencyRepository
import com.example.firestationops.domain.repository.InspectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InspectionUiState(
    val isLoading: Boolean = true,
    val template: InspectionTemplate? = null,
    val apparatus: Apparatus? = null,
    val responses: Map<String, InspectionResponse> = emptyMap(),
    val isSubmitting: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val inspectionId: String? = null,
    val startedAt: Long? = null,
    val isValid: Boolean = true,
    val submittedReport: InspectionReport? = null,
    val odometerMiles: Int? = null,
    val fluidOil: String? = null,
    val fluidTransmission: String? = null,
    val fluidFuel: String? = null,
    val fluidAntifreeze: String? = null,
    val fluidPowerSteering: String? = null,
    val availableTemplates: List<InspectionTemplate> = emptyList(),
    val needsTemplateSelection: Boolean = false
)

class InspectionViewModel(
    private val apparatusId: String,
    private val member: Member,
    private val inspectionRepository: InspectionRepository,
    private val deficiencyRepository: DeficiencyRepository,
    private val apparatusRepository: com.example.firestationops.domain.repository.ApparatusRepository,
    private val attachmentRepository: com.example.firestationops.domain.repository.AttachmentRepository,
    private val syncAttachmentCache: SyncAttachmentCache? = null,
    private val syncCoordinator: SyncCoordinator? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val initialTemplateId: String? = null
) {
    private val _uiState = MutableStateFlow(InspectionUiState())
    val uiState: StateFlow<InspectionUiState> = _uiState.asStateFlow()
    val attachmentsById: StateFlow<Map<String, Attachment>> = attachmentRepository
        .getAttachmentsByDepartment(member.departmentId)
        .map { attachments -> attachments.associateBy { it.id } }
        .stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0), emptyMap())

    init {
        loadData()
    }

    fun selectTemplate(templateId: String) {
        scope.launch {
            applyTemplateSelection(templateId = templateId, draft = null)
        }
    }

    private fun loadData() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val apparatusResult = apparatusRepository.getApparatus(apparatusId)
            val apparatus = apparatusResult.getOrNull()

            if (apparatus == null) {
                _uiState.update { it.copy(isLoading = false, error = "Apparatus not found") }
                return@launch
            }

            val templates = inspectionRepository.getActiveTemplates(member.departmentId).first()
            val eligible = TemplateAssignmentRules.resolveEligibleTemplates(apparatus, templates)
            val draft = inspectionRepository.getLatestDraft(apparatusId).getOrNull()

            when {
                draft != null -> applyTemplateSelection(templateId = draft.templateId, draft = draft, apparatus = apparatus, eligible = eligible)
                initialTemplateId != null && eligible.any { it.id == initialTemplateId } ->
                    applyTemplateSelection(templateId = initialTemplateId, draft = null, apparatus = apparatus, eligible = eligible)
                eligible.size == 1 ->
                    applyTemplateSelection(templateId = eligible.first().id, draft = null, apparatus = apparatus, eligible = eligible)
                eligible.isEmpty() ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            apparatus = apparatus,
                            error = "No active template assigned for ${apparatus.radioName}."
                        )
                    }
                else ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            apparatus = apparatus,
                            availableTemplates = eligible,
                            needsTemplateSelection = true,
                            error = null
                        )
                    }
            }
        }
    }

    private suspend fun applyTemplateSelection(
        templateId: String,
        draft: Inspection?,
        apparatus: Apparatus? = _uiState.value.apparatus,
        eligible: List<InspectionTemplate> = _uiState.value.availableTemplates
    ) {
        val resolvedApparatus = apparatus ?: apparatusRepository.getApparatus(apparatusId).getOrNull()
        if (resolvedApparatus == null) {
            _uiState.update { it.copy(isLoading = false, error = "Apparatus not found") }
            return
        }
        val templateResult = inspectionRepository.getTemplate(templateId)
        templateResult.onSuccess { template ->
            val matchingDraft = draft?.takeIf { it.templateId == template.id }
            val initialResponses = if (matchingDraft != null) {
                matchingDraft.responses.associateBy { it.itemId }
            } else {
                template.items.associate { item ->
                    item.id to InspectionResponse(
                        itemId = item.id,
                        status = InspectionStatus.PASS,
                        expectedQuantity = item.expectedQuantity
                    )
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    template = template,
                    apparatus = resolvedApparatus,
                    responses = initialResponses,
                    inspectionId = matchingDraft?.id,
                    startedAt = matchingDraft?.startedAt ?: currentTimeMillis(),
                    odometerMiles = matchingDraft?.odometerMiles,
                    fluidOil = matchingDraft?.fluidOil,
                    fluidTransmission = matchingDraft?.fluidTransmission,
                    fluidFuel = matchingDraft?.fluidFuel,
                    fluidAntifreeze = matchingDraft?.fluidAntifreeze,
                    fluidPowerSteering = matchingDraft?.fluidPowerSteering,
                    availableTemplates = eligible.ifEmpty {
                        listOf(template)
                    },
                    needsTemplateSelection = false,
                    isValid = InspectionValidationRules.isValid(template, initialResponses),
                    error = null
                )
            }
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    isLoading = false,
                    apparatus = resolvedApparatus,
                    error = error.message ?: "Failed to load template"
                )
            }
        }
    }

    fun updateResponse(itemId: String, status: InspectionStatus, severity: DeficiencySeverity? = null, note: String? = null, attachmentIds: List<String>? = null) {
        _uiState.update { state ->
            val newResponses = state.responses.toMutableMap()
            val currentResponse = newResponses[itemId]
            val item = state.template?.items?.find { it.id == itemId }
            newResponses[itemId] = InspectionResponse(
                itemId = itemId, 
                status = status, 
                note = note, 
                severity = severity,
                attachmentIds = attachmentIds ?: currentResponse?.attachmentIds ?: emptyList(),
                actualQuantity = currentResponse?.actualQuantity,
                expectedQuantity = currentResponse?.expectedQuantity ?: item?.expectedQuantity
            )
            val isValid = InspectionValidationRules.isValid(state.template, newResponses)
            state.copy(responses = newResponses, isValid = isValid)
        }
        saveDraft()
    }

    fun updateActualQuantity(itemId: String, actualQuantity: Int) {
        _uiState.update { state ->
            val item = state.template?.items?.find { it.id == itemId } ?: return@update state
            val current = state.responses[itemId] ?: InspectionResponse(
                itemId = itemId,
                status = InspectionStatus.PASS,
                expectedQuantity = item.expectedQuantity
            )
            val newResponses = state.responses.toMutableMap()
            newResponses[itemId] = InspectionValidationRules.withActualQuantity(item, current, actualQuantity)
            state.copy(
                responses = newResponses,
                isValid = InspectionValidationRules.isValid(state.template, newResponses)
            )
        }
        saveDraft()
    }

    fun updateVehicleStatus(
        odometerMiles: Int? = _uiState.value.odometerMiles,
        fluidOil: String? = _uiState.value.fluidOil,
        fluidTransmission: String? = _uiState.value.fluidTransmission,
        fluidFuel: String? = _uiState.value.fluidFuel,
        fluidAntifreeze: String? = _uiState.value.fluidAntifreeze,
        fluidPowerSteering: String? = _uiState.value.fluidPowerSteering
    ) {
        _uiState.update {
            it.copy(
                odometerMiles = odometerMiles,
                fluidOil = fluidOil?.trim()?.takeIf { value -> value.isNotEmpty() },
                fluidTransmission = fluidTransmission?.trim()?.takeIf { value -> value.isNotEmpty() },
                fluidFuel = fluidFuel?.trim()?.takeIf { value -> value.isNotEmpty() },
                fluidAntifreeze = fluidAntifreeze?.trim()?.takeIf { value -> value.isNotEmpty() },
                fluidPowerSteering = fluidPowerSteering?.trim()?.takeIf { value -> value.isNotEmpty() }
            )
        }
        saveDraft()
    }

    fun markSectionPresent(category: String) {
        _uiState.update { state ->
            val template = state.template ?: return@update state
            val newResponses = InspectionChecklistSections.markSectionPresent(
                category = category,
                items = template.items,
                responses = state.responses
            )
            state.copy(
                responses = newResponses,
                isValid = InspectionValidationRules.isValid(template, newResponses)
            )
        }
        saveDraft()
    }

    fun addAttachment(itemId: String, localPath: String) {
        scope.launch {
            val attachmentId = "att-${randomUUID()}"
            val durablePath = syncAttachmentCache?.copyToAttachmentPath(attachmentId, localPath) ?: localPath
            val attachment = SyncStatusTransitions.attachmentForSave(
                Attachment(
                    id = attachmentId,
                    departmentId = member.departmentId,
                    localUri = durablePath,
                    createdAt = currentTimeMillis(),
                    createdByUserId = member.id
                )
            )
            attachmentRepository.saveAttachment(attachment)

            _uiState.update { state ->
                val newResponses = state.responses.toMutableMap()
                val currentResponse = newResponses[itemId] ?: return@update state
                val newAttachmentIds = currentResponse.attachmentIds + attachment.id
                newResponses[itemId] = currentResponse.copy(attachmentIds = newAttachmentIds)
                state.copy(responses = newResponses)
            }
            saveDraft()

            val coordinator = syncCoordinator
            if (coordinator != null && coordinator.isAvailable()) {
                launch(Dispatchers.Default) {
                    coordinator.syncDepartment(member.departmentId)
                }
            }
        }
    }

    fun retryAttachment(attachmentId: String) {
        scope.launch {
            attachmentRepository.retryUpload(attachmentId)
            val coordinator = syncCoordinator
            if (coordinator != null && coordinator.isAvailable()) {
                coordinator.syncDepartment(member.departmentId)
            }
        }
    }

    private fun saveDraft() {
        val state = _uiState.value
        val template = state.template ?: return
        
        scope.launch {
            // Assign an ID if we don't have one yet
            val currentId = state.inspectionId ?: "insp-${currentTimeMillis()}"
            if (state.inspectionId == null) {
                _uiState.update { it.copy(inspectionId = currentId) }
            }

            val inspection = SyncStatusTransitions.inspectionForDraft(
                Inspection(
                    id = currentId,
                    templateId = template.id,
                    apparatusId = apparatusId,
                    departmentId = member.departmentId,
                    startedAt = state.startedAt ?: currentTimeMillis(),
                    completedAt = null,
                    startedByUserId = member.id,
                    responses = state.responses.values.toList(),
                    isFinalized = false,
                    odometerMiles = state.odometerMiles,
                    fluidOil = state.fluidOil,
                    fluidTransmission = state.fluidTransmission,
                    fluidFuel = state.fluidFuel,
                    fluidAntifreeze = state.fluidAntifreeze,
                    fluidPowerSteering = state.fluidPowerSteering
                )
            )
            inspectionRepository.saveInspection(inspection)
        }
    }

    fun submit() {
        val state = _uiState.value
        val template = state.template ?: return
        val apparatus = state.apparatus ?: return
        val inspectionId = state.inspectionId ?: "insp-${currentTimeMillis()}"

        val validationError = InspectionValidationRules.validateSubmission(template, state.responses)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }

            val inspection = SyncStatusTransitions.inspectionForSubmit(
                Inspection(
                    id = inspectionId,
                    templateId = template.id,
                    apparatusId = apparatusId,
                    departmentId = member.departmentId,
                    startedAt = state.startedAt ?: currentTimeMillis(),
                    completedAt = currentTimeMillis(),
                    startedByUserId = member.id,
                    responses = state.responses.values.toList(),
                    isFinalized = true,
                    odometerMiles = state.odometerMiles,
                    fluidOil = state.fluidOil,
                    fluidTransmission = state.fluidTransmission,
                    fluidFuel = state.fluidFuel,
                    fluidAntifreeze = state.fluidAntifreeze,
                    fluidPowerSteering = state.fluidPowerSteering
                )
            )

            // Save inspection
            val result = inspectionRepository.saveInspection(inspection)
            
            if (result.isSuccess) {
                var marksOutOfService = false
                
                // Create deficiencies for failed items
                state.responses.values.filter { it.status == InspectionStatus.FAIL }.forEach { response ->
                    val item = template.items.find { it.id == response.itemId }
                    val severity = response.severity ?: DeficiencySeverity.REPAIR_NEEDED
                    if (severity == DeficiencySeverity.OUT_OF_SERVICE) {
                        marksOutOfService = true
                    }
                    val quantityNote = if (
                        response.actualQuantity != null &&
                        (response.expectedQuantity ?: item?.expectedQuantity) != null
                    ) {
                        val expected = response.expectedQuantity ?: item?.expectedQuantity
                        "Found ${response.actualQuantity} of $expected. "
                    } else {
                        ""
                    }
                    
                    val deficiency = SyncStatusTransitions.deficiencyForSave(
                        Deficiency(
                            id = "def-${currentTimeMillis()}-${response.itemId}",
                            inspectionId = inspection.id,
                            apparatusId = apparatusId,
                            departmentId = member.departmentId,
                            title = "Failed: ${item?.text ?: "Unknown item"}",
                            description = quantityNote + (response.note ?: "No note provided"),
                            severity = severity,
                            status = DeficiencyStatus.OPEN,
                            createdAt = currentTimeMillis(),
                            createdByUserId = member.id,
                            attachmentIds = response.attachmentIds
                        )
                    )
                    deficiencyRepository.saveDeficiency(deficiency)
                }

                if (marksOutOfService) {
                    apparatusRepository.updateApparatusStatus(apparatusId, ApparatusStatus.OUT_OF_SERVICE)
                }

                val report = InspectionReportBuilder.build(
                    inspectionId = inspection.id,
                    apparatus = apparatus,
                    template = template,
                    completedAt = inspection.completedAt ?: currentTimeMillis(),
                    inspectorName = member.fullName,
                    responses = state.responses
                )
                
                _uiState.update { it.copy(isSubmitting = false, isSuccess = true, submittedReport = report) }
            } else {
                _uiState.update { it.copy(isSubmitting = false, error = "Failed to save inspection") }
            }
        }
    }

    suspend fun exportCsv(fileExporter: FileExporter): ExportResult {
        val report = _uiState.value.submittedReport
            ?: return ExportResult.Error("No inspection report is available to export")
        val fileName = "${InspectionReportBuilder.suggestedFileBaseName(report)}.csv"
        return fileExporter.saveTextFile(fileName, InspectionCsvExporter.export(report))
    }

    suspend fun exportPdf(fileExporter: FileExporter): ExportResult {
        val report = _uiState.value.submittedReport
            ?: return ExportResult.Error("No inspection report is available to export")
        val fileName = "${InspectionReportBuilder.suggestedFileBaseName(report)}.pdf"
        return fileExporter.saveBinaryFile(fileName, InspectionPdfExporter.export(report))
    }
}
