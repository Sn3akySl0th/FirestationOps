package com.example.firestationops.domain.repository.persistent

import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.bootstrap.DemoDepartmentSeeder
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.Station
import com.example.firestationops.domain.repository.ApparatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class PersistentApparatusRepository(private val database: FirestationOpsDatabase) : ApparatusRepository {
    private val _apparatus = MutableStateFlow<List<Apparatus>>(emptyList())
    private val _stations = MutableStateFlow<List<Station>>(emptyList())

    init {
        DemoDepartmentSeeder.ensureDemoData(database, "mock-dept-id")
        refresh()
    }

    private fun refresh() {
        _apparatus.value = database.getAllApparatus()
        _stations.value = database.getAllStations()
    }

    fun ensureDepartmentData(departmentId: String) {
        DemoDepartmentSeeder.ensureDemoData(database, departmentId)
        refreshCatalog()
    }

    fun refreshCatalog() {
        refresh()
    }

    override fun getStations(departmentId: String): Flow<List<Station>> =
        _stations.asStateFlow().map { list -> list.filter { it.departmentId == departmentId } }

    override fun getApparatusByDepartment(departmentId: String): Flow<List<Apparatus>> =
        _apparatus.asStateFlow().map { list -> list.filter { it.departmentId == departmentId } }

    override fun getApparatusByStation(stationId: String): Flow<List<Apparatus>> = 
        _apparatus.asStateFlow().map { list -> list.filter { it.stationId == stationId } }

    override suspend fun getApparatus(id: String): Result<Apparatus> = 
        database.getAllApparatus().find { it.id == id }?.let { Result.success(it) } 
            ?: Result.failure(Exception("Apparatus not found"))

    override suspend fun getStation(id: String): Result<Station> = 
        database.getAllStations().find { it.id == id }?.let { Result.success(it) } 
            ?: Result.failure(Exception("Station not found"))

    override suspend fun updateApparatusStatus(id: String, status: ApparatusStatus): Result<Unit> {
        database.updateApparatusStatus(id, status)
        refresh()
        return Result.success(Unit)
    }
}
