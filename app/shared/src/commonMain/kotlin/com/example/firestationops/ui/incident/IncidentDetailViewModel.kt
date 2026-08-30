package com.example.firestationops.ui.incident

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.domain.IncidentAssignmentRules
import com.example.firestationops.domain.IncidentWorkflowRules
import com.example.firestationops.domain.model.*
import com.example.firestationops.domain.repository.ApparatusRepository
import com.example.firestationops.domain.repository.DepartmentRepository
import com.example.firestationops.domain.repository.IncidentRepository
import com.example.firestationops.randomUUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UnitAssignmentItem(
    val assignment: IncidentUnitAssignment,
    val apparatusLabel: String
)

data class PersonnelAssignmentItem(
    val assignment: PersonnelAssignment,
    val memberLabel: String
)

data class IncidentDetailUiState(
    val isLoading: Boolean = true,
    val incidentId: String? = null,
    val title: String = "",
    val summary: String = "",
    val locationDescription: String = "",
    val incidentType: IncidentType = IncidentType.OTHER,
    val status: IncidentStatus = IncidentStatus.DRAFT,
    val unitAssignments: List<UnitAssignmentItem> = emptyList(),
    val personnelAssignments: List<PersonnelAssignmentItem> = emptyList(),
    val availableApparatus: List<Apparatus> = emptyList(),
    val availableMembers: List<Member> = emptyList(),
    val timeline: List<CommandLogEntry> = emptyList(),
    val newLogMessage: String = "",
    val correctingEntryId: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val infoMessage: String? = null,
    val canEditFields: Boolean = true,
    val canAppendLog: Boolean = true,
    val canManageAssignments: Boolean = false
)

class IncidentDetailViewModel(
    private val incidentId: String?,
    private val member: Member,
    private val incidentRepository: IncidentRepository,
    private val apparatusRepository: ApparatusRepository,
    private val departmentRepository: DepartmentRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    collectionScope: CoroutineScope = scope
) {
    private val timelineScope = collectionScope
    private val _uiState = MutableStateFlow(IncidentDetailUiState())
    val uiState: StateFlow<IncidentDetailUiState> = _uiState.asStateFlow()

    private var currentIncident: Incident? = null
    private var timelineJob: kotlinx.coroutines.Job? = null
    private var unitAssignmentsJob: kotlinx.coroutines.Job? = null
    private var personnelAssignmentsJob: kotlinx.coroutines.Job? = null

    private var apparatusById: Map<String, Apparatus> = emptyMap()
    private var membersById: Map<String, Member> = emptyMap()
    private var latestUnitAssignments: List<IncidentUnitAssignment> = emptyList()
    private var latestPersonnelAssignments: List<PersonnelAssignment> = emptyList()

    init {
        loadResources()
        load()
    }

    private fun loadResources() {
        scope.launch {
            apparatusRepository.getApparatusByDepartment(member.departmentId)
                .first()
                .let { apparatusList ->
                    apparatusById = apparatusList.associateBy { it.id }
                    updateAssignmentBoard()
                }

            departmentRepository.getMembersByDepartment(member.departmentId)
                .onSuccess { members ->
                    membersById = members.associateBy { it.id }
                    updateAssignmentBoard()
                }
        }
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
                observeIncidentData(incident.id)
                _uiState.value = IncidentDetailUiState(
                    isLoading = false,
                    incidentId = incident.id,
                    canEditFields = true,
                    canAppendLog = true,
                    canManageAssignments = true
                )
                return@launch
            }

            incidentRepository.getIncident(incidentId).onSuccess { incident ->
                currentIncident = incident
                observeIncidentData(incident.id)
                applyIncident(incident)
            }.onFailure {
                _uiState.update { state ->
                    state.copy(isLoading = false, error = it.message ?: "Incident not found")
                }
            }
        }
    }

    private fun observeIncidentData(id: String) {
        observeTimeline(id)
        unitAssignmentsJob?.cancel()
        unitAssignmentsJob = incidentRepository.getUnitAssignments(id)
            .onEach { assignments ->
                latestUnitAssignments = assignments
                updateAssignmentBoard()
            }
            .launchIn(timelineScope)

        personnelAssignmentsJob?.cancel()
        personnelAssignmentsJob = incidentRepository.getPersonnelAssignments(id)
            .onEach { assignments ->
                latestPersonnelAssignments = assignments
                updateAssignmentBoard()
            }
            .launchIn(timelineScope)
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
            canAppendLog = IncidentWorkflowRules.canAppendLogEntry(incident),
            canManageAssignments = IncidentAssignmentRules.canManageAssignments(incident)
        )
        updateAssignmentBoard()
    }

    private fun updateAssignmentBoard() {
        val incident = currentIncident ?: return
        val activeUnitIds = IncidentAssignmentRules.activeUnitAssignments(latestUnitAssignments)
            .map { it.apparatusId }
            .toSet()
        val activeMemberIds = IncidentAssignmentRules.activePersonnelAssignments(latestPersonnelAssignments)
            .map { it.memberId }
            .toSet()

        _uiState.update { state ->
            state.copy(
                unitAssignments = latestUnitAssignments.map { assignment ->
                    UnitAssignmentItem(
                        assignment = assignment,
                        apparatusLabel = apparatusById[assignment.apparatusId]?.radioName ?: assignment.apparatusId
                    )
                },
                personnelAssignments = latestPersonnelAssignments.map { assignment ->
                    PersonnelAssignmentItem(
                        assignment = assignment,
                        memberLabel = membersById[assignment.memberId]?.fullName ?: assignment.memberId
                    )
                },
                availableApparatus = apparatusById.values
                    .filter { it.id !in activeUnitIds }
                    .sortedBy { it.radioName },
                availableMembers = membersById.values
                    .filter { it.id !in activeMemberIds }
                    .sortedBy { it.fullName },
                canManageAssignments = IncidentAssignmentRules.canManageAssignments(incident)
            )
        }
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
                observeIncidentData(active.id)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        status = IncidentStatus.ACTIVE,
                        canEditFields = true,
                        canAppendLog = true,
                        canManageAssignments = true,
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
                        canManageAssignments = false,
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

    fun assignUnit(apparatusId: String) {
        val incident = currentIncident ?: return
        val validationError = IncidentAssignmentRules.validateNewUnitAssignment(
            incident = incident,
            apparatusId = apparatusId,
            existing = latestUnitAssignments
        )
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        scope.launch {
            val now = currentTimeMillis()
            val released = latestUnitAssignments.find {
                it.apparatusId == apparatusId && it.status == AssignmentStatus.RELEASED
            }
            val label = apparatusById[apparatusId]?.radioName ?: apparatusId
            val assignment = released?.copy(
                status = AssignmentStatus.ASSIGNED,
                assignedAt = now,
                assignedByUserId = member.id,
                updatedAt = now,
                updatedByUserId = member.id,
                syncStatus = SyncStatus.PENDING_SYNC
            ) ?: IncidentUnitAssignment(
                id = "unit-${randomUUID()}",
                incidentId = incident.id,
                departmentId = member.departmentId,
                apparatusId = apparatusId,
                assignedAt = now,
                assignedByUserId = member.id,
                updatedAt = now,
                updatedByUserId = member.id,
                syncStatus = SyncStatus.PENDING_SYNC
            )
            incidentRepository.saveUnitAssignment(assignment).onSuccess {
                latestUnitAssignments = latestUnitAssignments
                    .filter { it.id != assignment.id } + assignment
                updateAssignmentBoard()
                logAssignmentChange("Unit $label assigned")
            }.onFailure {
                _uiState.update { state -> state.copy(error = "Failed to assign unit") }
            }
        }
    }

    fun updateUnitStatus(assignmentId: String, newStatus: AssignmentStatus) {
        updateAssignmentStatus(
            assignmentId = assignmentId,
            newStatus = newStatus,
            findAssignment = { latestUnitAssignments.find { it.id == assignmentId } },
            label = { assignment ->
                apparatusById[(assignment as IncidentUnitAssignment).apparatusId]?.radioName
                    ?: assignment.apparatusId
            },
            save = { updated ->
                incidentRepository.saveUnitAssignment(updated as IncidentUnitAssignment)
            }
        )
    }

    fun assignPersonnel(memberId: String) {
        val incident = currentIncident ?: return
        val validationError = IncidentAssignmentRules.validateNewPersonnelAssignment(
            incident = incident,
            memberId = memberId,
            existing = latestPersonnelAssignments
        )
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        scope.launch {
            val now = currentTimeMillis()
            val released = latestPersonnelAssignments.find {
                it.memberId == memberId && it.status == AssignmentStatus.RELEASED
            }
            val label = membersById[memberId]?.fullName ?: memberId
            val assignment = released?.copy(
                status = AssignmentStatus.ASSIGNED,
                assignedAt = now,
                assignedByUserId = member.id,
                updatedAt = now,
                updatedByUserId = member.id,
                syncStatus = SyncStatus.PENDING_SYNC
            ) ?: PersonnelAssignment(
                id = "personnel-${randomUUID()}",
                incidentId = incident.id,
                departmentId = member.departmentId,
                memberId = memberId,
                assignedAt = now,
                assignedByUserId = member.id,
                updatedAt = now,
                updatedByUserId = member.id,
                syncStatus = SyncStatus.PENDING_SYNC
            )
            incidentRepository.savePersonnelAssignment(assignment).onSuccess {
                latestPersonnelAssignments = latestPersonnelAssignments
                    .filter { it.id != assignment.id } + assignment
                updateAssignmentBoard()
                logAssignmentChange("Personnel $label assigned")
            }.onFailure {
                _uiState.update { state -> state.copy(error = "Failed to assign personnel") }
            }
        }
    }

    fun updatePersonnelStatus(assignmentId: String, newStatus: AssignmentStatus) {
        updateAssignmentStatus(
            assignmentId = assignmentId,
            newStatus = newStatus,
            findAssignment = { latestPersonnelAssignments.find { it.id == assignmentId } },
            label = { assignment ->
                membersById[(assignment as PersonnelAssignment).memberId]?.fullName
                    ?: assignment.memberId
            },
            save = { updated ->
                incidentRepository.savePersonnelAssignment(updated as PersonnelAssignment)
            }
        )
    }

    private fun updateAssignmentStatus(
        assignmentId: String,
        newStatus: AssignmentStatus,
        findAssignment: () -> Any?,
        label: (Any) -> String,
        save: suspend (Any) -> Result<Unit>
    ) {
        val incident = currentIncident ?: return
        if (!IncidentAssignmentRules.canManageAssignments(incident)) {
            _uiState.update { it.copy(error = "Cannot update assignments on a closed incident") }
            return
        }

        val assignment = findAssignment() ?: return
        val currentStatus = when (assignment) {
            is IncidentUnitAssignment -> assignment.status
            is PersonnelAssignment -> assignment.status
            else -> return
        }
        val validationError = IncidentAssignmentRules.validateStatusTransition(currentStatus, newStatus)
        if (validationError != null) {
            _uiState.update { it.copy(error = validationError) }
            return
        }

        scope.launch {
            val now = currentTimeMillis()
            val updated = when (assignment) {
                is IncidentUnitAssignment -> assignment.copy(
                    status = newStatus,
                    updatedAt = now,
                    updatedByUserId = member.id,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
                is PersonnelAssignment -> assignment.copy(
                    status = newStatus,
                    updatedAt = now,
                    updatedByUserId = member.id,
                    syncStatus = SyncStatus.PENDING_SYNC
                )
                else -> return@launch
            }
            save(updated).onSuccess {
                when (updated) {
                    is IncidentUnitAssignment -> {
                        latestUnitAssignments = latestUnitAssignments
                            .filter { it.id != updated.id } + updated
                    }
                    is PersonnelAssignment -> {
                        latestPersonnelAssignments = latestPersonnelAssignments
                            .filter { it.id != updated.id } + updated
                    }
                }
                updateAssignmentBoard()
                val resourceLabel = label(assignment)
                logAssignmentChange(
                    IncidentAssignmentRules.statusLogMessage(resourceLabel, currentStatus, newStatus)
                )
            }.onFailure {
                _uiState.update { state -> state.copy(error = "Failed to update assignment status") }
            }
        }
    }

    private fun logAssignmentChange(message: String) {
        scope.launch {
            val incident = currentIncident ?: return@launch
            val entry = CommandLogEntry(
                id = "log-${randomUUID()}",
                incidentId = incident.id,
                departmentId = member.departmentId,
                message = message,
                createdAt = currentTimeMillis(),
                createdByUserId = member.id,
                syncStatus = SyncStatus.PENDING_SYNC
            )
            incidentRepository.appendCommandLogEntry(entry)
            _uiState.update { it.copy(infoMessage = message) }
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
