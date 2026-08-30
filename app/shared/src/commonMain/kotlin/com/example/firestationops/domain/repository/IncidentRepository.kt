package com.example.firestationops.domain.repository

import com.example.firestationops.domain.model.CommandLogEntry
import com.example.firestationops.domain.model.Incident
import com.example.firestationops.domain.model.IncidentUnitAssignment
import com.example.firestationops.domain.model.PersonnelAssignment
import kotlinx.coroutines.flow.Flow

interface IncidentRepository {
    fun getIncidentsByDepartment(departmentId: String): Flow<List<Incident>>
    suspend fun getIncident(id: String): Result<Incident>
    suspend fun saveIncident(incident: Incident): Result<Unit>
    fun getCommandLogEntries(incidentId: String): Flow<List<CommandLogEntry>>
    suspend fun appendCommandLogEntry(entry: CommandLogEntry): Result<Unit>
    fun getUnitAssignments(incidentId: String): Flow<List<IncidentUnitAssignment>>
    fun getPersonnelAssignments(incidentId: String): Flow<List<PersonnelAssignment>>
    suspend fun saveUnitAssignment(assignment: IncidentUnitAssignment): Result<Unit>
    suspend fun savePersonnelAssignment(assignment: PersonnelAssignment): Result<Unit>
}
