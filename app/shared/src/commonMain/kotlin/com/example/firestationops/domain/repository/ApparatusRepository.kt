package com.example.firestationops.domain.repository

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.Station
import kotlinx.coroutines.flow.Flow

interface ApparatusRepository {
    fun getStations(departmentId: String): Flow<List<Station>>
    fun getApparatusByDepartment(departmentId: String): Flow<List<Apparatus>>
    fun getApparatusByStation(stationId: String): Flow<List<Apparatus>>
    suspend fun getApparatus(id: String): Result<Apparatus>
    suspend fun getStation(id: String): Result<Station>
}
