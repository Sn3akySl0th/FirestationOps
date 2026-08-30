package com.example.firestationops.domain.repository.persistent

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.model.*
import com.example.firestationops.domain.repository.IncidentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class PersistentIncidentRepository(private val database: FirestationOpsDatabase) : IncidentRepository {
    private val _incidents = MutableStateFlow<List<Incident>>(emptyList())
    private val _commandLog = MutableStateFlow<List<CommandLogEntry>>(emptyList())
    private val _unitAssignments = MutableStateFlow<List<IncidentUnitAssignment>>(emptyList())
    private val _personnelAssignments = MutableStateFlow<List<PersonnelAssignment>>(emptyList())

    init {
        if (database.getAllIncidents().isEmpty()) {
            seed()
        }
        refresh()
    }

    private fun refresh() {
        _incidents.value = database.getAllIncidents()
        _commandLog.value = database.getAllIncidents().flatMap { database.getCommandLogEntriesByIncident(it.id) }
        _unitAssignments.value = database.getAllIncidents().flatMap { database.getUnitAssignmentsByIncident(it.id) }
        _personnelAssignments.value = database.getAllIncidents().flatMap { database.getPersonnelAssignmentsByIncident(it.id) }
    }

    private fun seed() {
        val now = currentTimeMillis()
        val incidentId = "inc-seed-1"
        database.insertIncident(
            Incident(
                id = incidentId,
                departmentId = "mock-dept-id",
                title = "Training burn evolution",
                summary = "Controlled training exercise at the training grounds.",
                locationDescription = "Department training tower",
                incidentType = IncidentType.TRAINING,
                status = IncidentStatus.ACTIVE,
                createdAt = now - 3_600_000L,
                createdByUserId = "admin-1",
                updatedAt = now - 3_600_000L,
                updatedByUserId = "admin-1",
                syncStatus = SyncStatus.SYNCED
            )
        )
        database.insertCommandLogEntry(
            CommandLogEntry(
                id = "log-seed-1",
                incidentId = incidentId,
                departmentId = "mock-dept-id",
                message = "Command established. Training scenario briefing complete.",
                createdAt = now - 3_000_000L,
                createdByUserId = "admin-1",
                syncStatus = SyncStatus.SYNCED
            )
        )
        database.insertCommandLogEntry(
            CommandLogEntry(
                id = "log-seed-2",
                incidentId = incidentId,
                departmentId = "mock-dept-id",
                message = "Engine 1 on scene and staged.",
                createdAt = now - 2_400_000L,
                createdByUserId = "admin-1",
                incidentTimestamp = now - 2_500_000L,
                syncStatus = SyncStatus.SYNCED
            )
        )
        database.insertUnitAssignment(
            IncidentUnitAssignment(
                id = "unit-seed-1",
                incidentId = incidentId,
                departmentId = "mock-dept-id",
                apparatusId = "ap-1",
                status = AssignmentStatus.ON_SCENE,
                assignedAt = now - 3_200_000L,
                assignedByUserId = "admin-1",
                updatedAt = now - 2_400_000L,
                updatedByUserId = "admin-1",
                syncStatus = SyncStatus.SYNCED
            )
        )
    }

    override fun getIncidentsByDepartment(departmentId: String): Flow<List<Incident>> =
        _incidents.asStateFlow().map { list ->
            list.filter { it.departmentId == departmentId }
                .sortedByDescending { it.updatedAt }
        }

    override suspend fun getIncident(id: String): Result<Incident> =
        database.getIncidentById(id)?.let { Result.success(it) }
            ?: Result.failure(Exception("Incident not found"))

    override suspend fun saveIncident(incident: Incident): Result<Unit> {
        database.insertIncident(incident)
        refresh()
        return Result.success(Unit)
    }

    override fun getCommandLogEntries(incidentId: String): Flow<List<CommandLogEntry>> =
        _commandLog.asStateFlow().map { list ->
            list.filter { it.incidentId == incidentId }
        }

    override suspend fun appendCommandLogEntry(entry: CommandLogEntry): Result<Unit> {
        database.insertCommandLogEntry(entry)
        refresh()
        return Result.success(Unit)
    }

    override fun getUnitAssignments(incidentId: String): Flow<List<IncidentUnitAssignment>> =
        _unitAssignments.asStateFlow().map { list ->
            list.filter { it.incidentId == incidentId }
        }

    override fun getPersonnelAssignments(incidentId: String): Flow<List<PersonnelAssignment>> =
        _personnelAssignments.asStateFlow().map { list ->
            list.filter { it.incidentId == incidentId }
        }

    override suspend fun saveUnitAssignment(assignment: IncidentUnitAssignment): Result<Unit> {
        database.insertUnitAssignment(assignment)
        refresh()
        return Result.success(Unit)
    }

    override suspend fun savePersonnelAssignment(assignment: PersonnelAssignment): Result<Unit> {
        database.insertPersonnelAssignment(assignment)
        refresh()
        return Result.success(Unit)
    }
}
