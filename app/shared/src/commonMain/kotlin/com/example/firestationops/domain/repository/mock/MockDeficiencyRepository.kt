package com.example.firestationops.domain.repository.mock

import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.DeficiencyStatus
import com.example.firestationops.domain.repository.DeficiencyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import com.example.firestationops.currentTimeMillis

class MockDeficiencyRepository : DeficiencyRepository {
    private val deficiencies = MutableStateFlow<List<Deficiency>>(emptyList())

    override fun getDeficienciesForApparatus(apparatusId: String): Flow<List<Deficiency>> = 
        deficiencies.map { list -> list.filter { it.apparatusId == apparatusId } }

    override fun getOpenDeficiencies(departmentId: String): Flow<List<Deficiency>> =
        deficiencies.map { list -> 
            list.filter { it.departmentId == departmentId && (it.status == DeficiencyStatus.OPEN || it.status == DeficiencyStatus.ASSIGNED) } 
        }

    override suspend fun saveDeficiency(deficiency: Deficiency): Result<Unit> {
        deficiencies.update { (it.filter { d -> d.id != deficiency.id } + deficiency) }
        return Result.success(Unit)
    }

    override suspend fun getDeficiency(id: String): Result<Deficiency> = 
        deficiencies.value.find { it.id == id }?.let { Result.success(it) } 
            ?: Result.failure(Exception("Deficiency not found"))

    override suspend fun resolveDeficiency(id: String, userId: String, note: String): Result<Unit> {
        val now = currentTimeMillis()
        deficiencies.update { list ->
            list.map { d ->
                if (d.id == id) {
                    d.copy(
                        status = DeficiencyStatus.RESOLVED,
                        resolvedAt = now,
                        resolvedByUserId = userId,
                        resolutionNote = note
                    )
                } else d
            }
        }
        return Result.success(Unit)
    }
}
