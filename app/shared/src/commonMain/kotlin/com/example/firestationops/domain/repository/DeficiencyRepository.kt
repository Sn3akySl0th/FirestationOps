package com.example.firestationops.domain.repository

import com.example.firestationops.domain.model.Deficiency
import kotlinx.coroutines.flow.Flow

interface DeficiencyRepository {
    fun getDeficienciesForApparatus(apparatusId: String): Flow<List<Deficiency>>
    fun getOpenDeficiencies(departmentId: String): Flow<List<Deficiency>>
    suspend fun saveDeficiency(deficiency: Deficiency): Result<Unit>
    suspend fun getDeficiency(id: String): Result<Deficiency>
    suspend fun resolveDeficiency(id: String, userId: String, note: String): Result<Unit>
}
