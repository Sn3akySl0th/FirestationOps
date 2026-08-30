package com.example.firestationops.ui.inspection

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.randomUUID
import com.example.firestationops.domain.export.InspectionCsvExporter
import com.example.firestationops.domain.export.InspectionPdfExporter
import com.example.firestationops.domain.export.InspectionReport
import com.example.firestationops.domain.export.InspectionReportBuilder
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
    val submittedReport: InspectionReport? = null
)

class InspectionViewModel(
    private val apparatusId: String,
    private val member: Member,
    private val inspectionRepository: InspectionRepository,
    private val deficiencyRepository: DeficiencyRepository,
    private val apparatusRepository: com.example.firestationops.domain.repository.ApparatusRepository,
    private val attachmentRepository: com.example.firestationops.domain.repository.AttachmentRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(InspectionUiState())
    val uiState: StateFlow<InspectionUiState> = _uiState.asStateFlow()

    init {
        loadData()
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

            // Check for existing draft first
            val draftResult = inspectionRepository.getLatestDraft(apparatusId)
            val draft = draftResult.getOrNull()

            val templateId = draft?.templateId
            val templateResult = if (templateId != null) {
                inspectionRepository.getTemplate(templateId)
            } else {
                inspectionRepository.getTemplatesByApparatusType(member.departmentId, apparatus.type)
                    .firstOrNull()
                    ?.firstOrNull { it.isActive }
                    ?.let { Result.success(it) } 
                    ?: Result.failure(Exception("No active template found for ${apparatus.type}"))
            }

            templateResult.onSuccess { template ->
                val initialResponses = if (draft != null) {
                    draft.responses.associateBy { it.itemId }
                } else {
                    template.items.associate { item ->
                        item.id to InspectionResponse(item.id, InspectionStatus.PASS)
                    }
                }

                _uiState.update { it.copy(
                    isLoading = false, 
                    template = template,
                    apparatus = apparatus,
                    responses = initialResponses,
                    inspectionId = draft?.id,
                    startedAt = draft?.startedAt ?: currentTimeMillis(),
                    isValid = validate(template, initialResponses)
                ) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load template") }
            }
        }
    }

    fun updateResponse(itemId: String, status: InspectionStatus, severity: DeficiencySeverity? = null, note: String? = null, attachmentIds: List<String>? = null) {
        _uiState.update { state ->
            val newResponses = state.responses.toMutableMap()
            val currentResponse = newResponses[itemId]
            newResponses[itemId] = InspectionResponse(
                itemId = itemId, 
                status = status, 
                note = note, 
                severity = severity,
                attachmentIds = attachmentIds ?: currentResponse?.attachmentIds ?: emptyList()
            )
            val isValid = validate(state.template, newResponses)
            state.copy(responses = newResponses, isValid = isValid)
        }
        saveDraft()
    }

    fun addAttachment(itemId: String, localPath: String) {
        scope.launch {
            val attachment = SyncStatusTransitions.attachmentForSave(
                Attachment(
                    id = "att-${randomUUID()}",
                    departmentId = member.departmentId,
                    localUri = localPath,
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
        }
    }

    private fun validate(template: InspectionTemplate?, responses: Map<String, InspectionResponse>): Boolean {
        if (template == null) return false
        return responses.values.none { response ->
            val item = template.items.find { it.id == response.itemId }
            response.status == InspectionStatus.FAIL && (
                (item?.requiresNoteOnFail == true || response.severity == DeficiencySeverity.OUT_OF_SERVICE) && 
                response.note.isNullOrBlank()
            )
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
                    isFinalized = false
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

        // Validation
        val invalidResponses = state.responses.values.filter { response ->
            val item = template.items.find { it.id == response.itemId }
            response.status == InspectionStatus.FAIL && (
                (item?.requiresNoteOnFail == true || response.severity == DeficiencySeverity.OUT_OF_SERVICE) && 
                response.note.isNullOrBlank()
            )
        }

        if (invalidResponses.isNotEmpty()) {
            _uiState.update { it.copy(error = "Notes are required for failed items.") }
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
                    isFinalized = true
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
                    
                    val deficiency = SyncStatusTransitions.deficiencyForSave(
                        Deficiency(
                            id = "def-${currentTimeMillis()}-${response.itemId}",
                            inspectionId = inspection.id,
                            apparatusId = apparatusId,
                            departmentId = member.departmentId,
                            title = "Failed: ${item?.text ?: "Unknown item"}",
                            description = response.note ?: "No note provided",
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
