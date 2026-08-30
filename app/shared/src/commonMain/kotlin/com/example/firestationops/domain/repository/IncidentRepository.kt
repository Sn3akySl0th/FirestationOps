package com.example.firestationops.domain.repository

import com.example.firestationops.domain.model.CommandLogEntry
import com.example.firestationops.domain.model.Incident
import kotlinx.coroutines.flow.Flow

interface IncidentRepository {
    fun getIncidentsByDepartment(departmentId: String): Flow<List<Incident>>
    suspend fun getIncident(id: String): Result<Incident>
    suspend fun saveIncident(incident: Incident): Result<Unit>
    fun getCommandLogEntries(incidentId: String): Flow<List<CommandLogEntry>>
    suspend fun appendCommandLogEntry(entry: CommandLogEntry): Result<Unit>
}
