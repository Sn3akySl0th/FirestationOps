package com.example.firestationops.domain.repository.mock

import com.example.firestationops.domain.model.CommandLogEntry
import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.model.IncidentUnitAssignment
import com.example.firestationops.domain.model.PersonnelAssignment
import com.example.firestationops.domain.repository.IncidentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class MockIncidentRepository : IncidentRepository {
    private val incidents = MutableStateFlow<List<Incident>>(emptyList())
    private val commandLog = MutableStateFlow<List<CommandLogEntry>>(emptyList())
    private val unitAssignments = MutableStateFlow<List<IncidentUnitAssignment>>(emptyList())
    private val personnelAssignments = MutableStateFlow<List<PersonnelAssignment>>(emptyList())

    override fun getIncidentsByDepartment(departmentId: String): Flow<List<Incident>> =
        incidents.map { list ->
            list.filter { it.departmentId == departmentId }.sortedByDescending { it.updatedAt }
        }

    override suspend fun getIncident(id: String): Result<Incident> =
        incidents.value.find { it.id == id }?.let { Result.success(it) }
            ?: Result.failure(Exception("Incident not found"))

    override suspend fun saveIncident(incident: Incident): Result<Unit> {
        incidents.update { list ->
            list.filter { it.id != incident.id } + incident
        }
        return Result.success(Unit)
    }

    override fun getCommandLogEntries(incidentId: String): Flow<List<CommandLogEntry>> =
        commandLog.map { list -> list.filter { it.incidentId == incidentId } }

    override suspend fun appendCommandLogEntry(entry: CommandLogEntry): Result<Unit> {
        commandLog.update { it + entry }
        return Result.success(Unit)
    }

    override fun getUnitAssignments(incidentId: String): Flow<List<IncidentUnitAssignment>> =
        unitAssignments.map { list -> list.filter { it.incidentId == incidentId } }

    override fun getPersonnelAssignments(incidentId: String): Flow<List<PersonnelAssignment>> =
        personnelAssignments.map { list -> list.filter { it.incidentId == incidentId } }

    override suspend fun saveUnitAssignment(assignment: IncidentUnitAssignment): Result<Unit> {
        unitAssignments.update { list ->
            list.filter { it.id != assignment.id } + assignment
        }
        return Result.success(Unit)
    }

    override suspend fun savePersonnelAssignment(assignment: PersonnelAssignment): Result<Unit> {
        personnelAssignments.update { list ->
            list.filter { it.id != assignment.id } + assignment
        }
        return Result.success(Unit)
    }
}
