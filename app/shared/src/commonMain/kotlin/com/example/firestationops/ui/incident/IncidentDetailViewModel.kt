package com.example.firestationops.ui.incident

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.domain.IncidentWorkflowRules
import com.example.firestationops.domain.model.*
import com.example.firestationops.domain.repository.IncidentRepository
import com.example.firestationops.randomUUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class IncidentDetailUiState(
    val isLoading: Boolean = true,
    val incidentId: String? = null,
    val title: String = "",
    val summary: String = "",
    val locationDescription: String = "",
    val incidentType: IncidentType = IncidentType.OTHER,
    val status: IncidentStatus = IncidentStatus.DRAFT,
    val timeline: List<CommandLogEntry> = emptyList(),
    val newLogMessage: String = "",
    val correctingEntryId: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,
    val canEditFields: Boolean = true,
    val canAppendLog: Boolean = true
)

class IncidentDetailViewModel(
    private val incidentId: String?,
    private val member: Member,
    private val incidentRepository: IncidentRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    collectionScope: CoroutineScope = scope
) {
    private val timelineScope = collectionScope
    private val _uiState = MutableStateFlow(IncidentDetailUiState())
    val uiState: StateFlow<IncidentDetailUiState> = _uiState.asStateFlow()

    private var currentIncident: Incident? = null
    private var timelineJob: kotlinx.coroutines.Job? = null

    init {
        load()
    }

    private fun load() {
        scope.launch {
            if (incidentId == null) {
                val now = currentTimeMillis()
                val incident = Incident(
                    id = "inc-${randomUUID()}",
                    departmentId = member.departmentId,
                    title = "",
                    createdAt = now,
                    createdByUserId = member.id,
                    updatedAt = now,
                    updatedByUserId = member.id
                )
                incidentRepository.saveIncident(incident)
                currentIncident = incident
                observeTimeline(incident.id)
                _uiState.value = IncidentDetailUiState(
                    isLoading = false,
                    incidentId = incident.id,
                    canEditFields = true,
                    canAppendLog = true
                )
                return@launch
            }

            incidentRepository.getIncident(incidentId).onSuccess { incident ->
                currentIncident = incident
                observeTimeline(incident.id)
                applyIncident(incident)
            }.onFailure {
                _uiState.update { state ->
                    state.copy(isLoading = false, error = it.message ?: "Incident not found")
                }
            }
        }
    }

    private fun observeTimeline(id: String) {
        timelineJob?.cancel()
        timelineJob = incidentRepository.getCommandLogEntries(id)
            .onEach { entries ->
                _uiState.update { state ->
                    state.copy(timeline = IncidentWorkflowRules.sortTimeline(entries))
                }
            }
            .launchIn(timelineScope)
    }

    private fun applyIncident(incident: Incident) {
        _uiState.value = IncidentDetailUiState(
            isLoading = false,
            incidentId = incident.id,
            title = incident.title,
            summary = incident.summary,
            locationDescription = incident.locationDescription,
            incidentType = incident.incidentType,
            status = incident.status,
            canEditFields = IncidentWorkflowRules.canEditIncidentFields(incident),
            canAppendLog = IncidentWorkflowRules.canAppendLogEntry(incident)
        )
    }

    fun updateTitle(value: String) {
        _uiState.update { it.copy(title = value, error = null) }
        saveDraft()
    }

    fun updateSummary(value: String) {
        _uiState.update { it.copy(summary = value, error = null) }
        saveDraft()
    }

    fun updateLocation(value: String) {
        _uiState.update { it.copy(locationDescription = value, error = null) }
        saveDraft()
    }

    fun updateIncidentType(value: IncidentType) {
        _uiState.update { it.copy(incidentType = value, error = null) }
        saveDraft()
    }

    fun updateNewLogMessage(value: String) {
        _uiState.update { it.copy(newLogMessage = value, error = null) }
    }

    fun startCorrection(entryId: String) {
        _uiState.update {
            it.copy(
                correctingEntryId = entryId,
                newLogMessage = "",
                error = null,
                infoMessage = null
            )
        }
    }

    fun cancelCorrection() {
        _uiState.update {
            it.copy(
                correctingEntryId = null,
                newLogMessage = "",
                error = null
            )
        }
    }

    private fun saveDraft() {
        val incident = buildIncidentFromState() ?: return
        if (!IncidentWorkflowRules.canEditIncidentFields(incident)) return

        scope.launch {
            val draft = incident.copy(
                updatedAt = currentTimeMillis(),
                updatedByUserId = member.id,
                syncStatus = SyncStatus.PENDING_SYNC
            )
            incidentRepository.saveIncident(draft)
            currentIncident = draft
        }
    }

    fun activateIncident() {
        val incident = buildIncidentFromState() ?: return
        IncidentWorkflowRules.validateActivation(incident)?.let { message ->
            _uiState.update { it.copy(error = message) }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val now = currentTimeMillis()
            val active = incident.copy(
                status = IncidentStatus.ACTIVE,
                updatedAt = now,
                updatedByUserId = member.id,
                syncStatus = SyncStatus.PENDING_SYNC
            )
            incidentRepository.saveIncident(active).onSuccess {
                currentIncident = active
                observeTimeline(active.id)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        status = IncidentStatus.ACTIVE,
                        canEditFields = true,
                        canAppendLog = true,
                        infoMessage = "Incident opened"
                    )
                }
            }.onFailure {
                _uiState.update { state -> state.copy(isSaving = false, error = "Failed to open incident") }
            }
        }
    }

    fun closeIncident() {
        val incident = buildIncidentFromState() ?: return
        IncidentWorkflowRules.validateClose(incident)?.let { message ->
            _uiState.update { it.copy(error = message) }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val now = currentTimeMillis()
            val closed = incident.copy(
                status = IncidentStatus.CLOSED,
                updatedAt = now,
                updatedByUserId = member.id,
                closedAt = now,
                closedByUserId = member.id,
                syncStatus = SyncStatus.PENDING_SYNC
            )
            incidentRepository.saveIncident(closed).onSuccess {
                currentIncident = closed
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        status = IncidentStatus.CLOSED,
                        canEditFields = false,
                        canAppendLog = false,
                        infoMessage = "Incident closed"
                    )
                }
            }.onFailure {
                _uiState.update { state -> state.copy(isSaving = false, error = "Failed to close incident") }
            }
        }
    }

    fun appendLogEntry() {
        appendTimelineEntry(
            entryType = CommandLogEntryType.LOG,
            correctsEntryId = null,
            successMessage = "Timeline entry added"
        )
    }

    fun appendCorrection() {
        val correctingEntryId = _uiState.value.correctingEntryId
        if (correctingEntryId == null) {
            _uiState.update { it.copy(error = "Select an entry to correct") }
            return
        }
        appendTimelineEntry(
            entryType = CommandLogEntryType.CORRECTION,
            correctsEntryId = correctingEntryId,
            successMessage = "Correction posted"
        )
    }

    private fun appendTimelineEntry(
        entryType: CommandLogEntryType,
        correctsEntryId: String?,
        successMessage: String
    ) {
        val state = _uiState.value
        val incident = currentIncident ?: return
        val validationError = IncidentWorkflowRules.validateLogEntry(
            incident = incident,
            message = state.newLogMessage,
            entryType = entryType,
            correctsEntryId = correctsEntryId
        )
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        scope.launch {
            val now = currentTimeMillis()
            val entry = CommandLogEntry(
                id = "log-${randomUUID()}",
                incidentId = incident.id,
                departmentId = member.departmentId,
                message = state.newLogMessage.trim(),
                entryType = entryType,
                createdAt = now,
                createdByUserId = member.id,
                correctsEntryId = correctsEntryId,
                syncStatus = SyncStatus.PENDING_SYNC
            )
            incidentRepository.appendCommandLogEntry(entry).onSuccess {
                _uiState.update {
                    it.copy(
                        newLogMessage = "",
                        correctingEntryId = null,
                        infoMessage = successMessage
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(error = "Failed to add timeline entry") }
            }
        }
    }

    private fun buildIncidentFromState(): Incident? {
        val base = currentIncident ?: return null
        val state = _uiState.value
        return base.copy(
            title = state.title.trim(),
            summary = state.summary.trim(),
            locationDescription = state.locationDescription.trim(),
            incidentType = state.incidentType,
            status = state.status
        )
    }
}
