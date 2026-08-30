package com.example.firestationops.domain.repository.persistent

import com.example.firestationops.db.FirestationOpsDatabase
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
        val stations = database.getAllStations()
        if (stations.isEmpty()) {
            seed()
        }
        refresh()
    }

    private fun refresh() {
        _apparatus.value = database.getAllApparatus()
        _stations.value = database.getAllStations()
    }

    private fun seed() {
        val s1 = Station(id = "st-1", departmentId = "mock-dept-id", name = "Station 1", address = "123 Main St")
        val s2 = Station(id = "st-2", departmentId = "mock-dept-id", name = "Station 2", address = "456 Oak St")
        database.insertStation(s1)
        database.insertStation(s2)

        database.insertApparatus(Apparatus(
            id = "ap-1", departmentId = "mock-dept-id", stationId = "st-1",
            name = "Engine 1", type = "Engine", radioName = "E1", status = ApparatusStatus.IN_SERVICE
        ))
        database.insertApparatus(Apparatus(
            id = "ap-2", departmentId = "mock-dept-id", stationId = "st-1",
            name = "Ladder 1", type = "Ladder", radioName = "L1", status = ApparatusStatus.IN_SERVICE
        ))
        database.insertApparatus(Apparatus(
            id = "ap-3", departmentId = "mock-dept-id", stationId = "st-2",
            name = "Engine 2", type = "Engine", radioName = "E2", status = ApparatusStatus.OUT_OF_SERVICE
        ))
        database.insertApparatus(Apparatus(
            id = "ap-4", departmentId = "mock-dept-id", stationId = "st-2",
            name = "Rescue 1", type = "Rescue", radioName = "R1", status = ApparatusStatus.IN_SERVICE
        ))
    }

    override fun getStations(departmentId: String): Flow<List<Station>> = _stations.asStateFlow()

    override fun getApparatusByDepartment(departmentId: String): Flow<List<Apparatus>> = _apparatus.asStateFlow()

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
