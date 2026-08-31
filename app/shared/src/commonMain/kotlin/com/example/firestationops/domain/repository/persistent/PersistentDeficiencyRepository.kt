package com.example.firestationops.domain.repository.persistent

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.bootstrap.DemoDepartmentSeeder
import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.DeficiencyStatus
import com.example.firestationops.domain.model.SyncStatus
import com.example.firestationops.domain.repository.DeficiencyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class PersistentDeficiencyRepository(private val database: FirestationOpsDatabase) : DeficiencyRepository {
    private val _deficiencies = MutableStateFlow<List<Deficiency>>(emptyList())

    init {
        if (database.getAllDeficiencies().isEmpty()) {
            seed()
        }
        refresh()
    }

    private fun refresh() {
        _deficiencies.value = database.getAllDeficiencies()
    }

    private fun seed() {
        val now = currentTimeMillis()
        database.insertDeficiency(
            Deficiency(
                id = "def-seed-e2",
                inspectionId = null,
                apparatusId = DemoDepartmentSeeder.APPARATUS_ENGINE_2,
                departmentId = "mock-dept-id",
                title = "Pump seal leak",
                description = "Minor hydraulic leak at pump seal — unit held out of service pending repair.",
                severity = DeficiencySeverity.OUT_OF_SERVICE,
                status = DeficiencyStatus.OPEN,
                createdAt = now - 86_400_000L,
                createdByUserId = "admin-1"
            )
        )
    }

    override fun getDeficienciesForApparatus(apparatusId: String): Flow<List<Deficiency>> = 
        _deficiencies.asStateFlow().map { list -> list.filter { it.apparatusId == apparatusId } }

    override fun getDeficienciesByDepartment(departmentId: String): Flow<List<Deficiency>> =
        _deficiencies.asStateFlow().map { list ->
            list.filter { it.departmentId == departmentId }
        }

    override fun getOpenDeficiencies(departmentId: String): Flow<List<Deficiency>> =
        _deficiencies.asStateFlow().map { list -> 
            list.filter { it.departmentId == departmentId && (it.status == DeficiencyStatus.OPEN || it.status == DeficiencyStatus.ASSIGNED) } 
        }

    override suspend fun saveDeficiency(deficiency: Deficiency): Result<Unit> {
        database.insertDeficiency(deficiency)
        refresh()
        return Result.success(Unit)
    }

    override suspend fun getDeficiency(id: String): Result<Deficiency> = 
        _deficiencies.value.find { it.id == id }?.let { Result.success(it) } 
            ?: Result.failure(Exception("Deficiency not found"))

    override suspend fun resolveDeficiency(id: String, userId: String, note: String): Result<Unit> {
        val now = currentTimeMillis()
        database.updateDeficiencyStatus(
            id = id,
            status = DeficiencyStatus.RESOLVED,
            resolvedAt = now,
            resolvedByUserId = userId,
            resolutionNote = note
        )
        refresh()
        return Result.success(Unit)
    }

    override suspend fun getPendingSyncDeficiencies(): Result<List<Deficiency>> =
        Result.success(database.getPendingSyncDeficiencies())

    override suspend fun updateSyncStatus(id: String, syncStatus: SyncStatus): Result<Unit> {
        database.updateDeficiencySyncStatus(id, syncStatus)
        refresh()
        return Result.success(Unit)
    }
}
