package com.example.firestationops.domain.repository.mock

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.Station
import com.example.firestationops.domain.repository.ApparatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class MockApparatusRepository : ApparatusRepository {
    private val stations = MutableStateFlow(
        listOf(
            Station(id = "st-1", departmentId = "mock-dept-id", name = "Station 1", address = "123 Main St"),
            Station(id = "st-2", departmentId = "mock-dept-id", name = "Station 2", address = "456 Oak St")
        )
    )

    private val apparatus = MutableStateFlow(
        listOf(
            Apparatus(
                id = "ap-1", departmentId = "mock-dept-id", stationId = "st-1",
                name = "Engine 1", type = "Engine", radioName = "E1", status = ApparatusStatus.IN_SERVICE
            ),
            Apparatus(
                id = "ap-2", departmentId = "mock-dept-id", stationId = "st-1",
                name = "Ladder 1", type = "Ladder", radioName = "L1", status = ApparatusStatus.IN_SERVICE
            ),
            Apparatus(
                id = "ap-3", departmentId = "mock-dept-id", stationId = "st-2",
                name = "Engine 2", type = "Engine", radioName = "E2", status = ApparatusStatus.OUT_OF_SERVICE
            ),
            Apparatus(
                id = "ap-4", departmentId = "mock-dept-id", stationId = "st-2",
                name = "Rescue 1", type = "Rescue", radioName = "R1", status = ApparatusStatus.IN_SERVICE
            )
        )
    )

    override fun getStations(departmentId: String): Flow<List<Station>> = stations

    override fun getApparatusByDepartment(departmentId: String): Flow<List<Apparatus>> = apparatus

    override fun getApparatusByStation(stationId: String): Flow<List<Apparatus>> = 
        apparatus.map { list -> list.filter { it.stationId == stationId } }

    override suspend fun getApparatus(id: String): Result<Apparatus> = 
        apparatus.value.find { it.id == id }?.let { Result.success(it) } 
            ?: Result.failure(Exception("Apparatus not found"))

    override suspend fun getStation(id: String): Result<Station> = 
        stations.value.find { it.id == id }?.let { Result.success(it) } 
            ?: Result.failure(Exception("Station not found"))

    override suspend fun updateApparatusStatus(id: String, status: ApparatusStatus): Result<Unit> {
        val current = apparatus.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(status = status)
            apparatus.value = current
            return Result.success(Unit)
        }
        return Result.failure(Exception("Apparatus not found"))
    }
}
