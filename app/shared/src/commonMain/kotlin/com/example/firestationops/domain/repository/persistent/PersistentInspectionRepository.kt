package com.example.firestationops.domain.repository.persistent

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.bootstrap.DemoDepartmentSeeder
import com.example.firestationops.domain.model.*
import com.example.firestationops.domain.repository.InspectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class PersistentInspectionRepository(private val database: FirestationOpsDatabase) : InspectionRepository {
    private val _templates = MutableStateFlow<List<InspectionTemplate>>(emptyList())
    private val _inspections = MutableStateFlow<List<Inspection>>(emptyList())

    init {
        DemoDepartmentSeeder.ensureDemoData(database, "mock-dept-id")
        seedDashboardDemoInspections()
        refresh()
    }

    private fun refresh() {
        _templates.value = database.getAllTemplates()
        _inspections.value = database.getAllInspections()
    }

    fun ensureDepartmentData(departmentId: String) {
        DemoDepartmentSeeder.ensureDemoData(database, departmentId)
        refreshCatalog()
    }

    fun refreshCatalog() {
        refresh()
    }

    private fun seedDashboardDemoInspections() {
        val departmentId = "mock-dept-id"
        if (database.getInspectionsByDepartment(departmentId).isNotEmpty()) return

        val now = currentTimeMillis()
        val twoDaysAgo = now - (2 * 86_400_000L)
        val oneHourAgo = now - (3_600_000L)

        database.insertInspection(
            Inspection(
                id = "insp-seed-e1",
                templateId = DemoDepartmentSeeder.TEMPLATE_ENGINE,
                apparatusId = DemoDepartmentSeeder.APPARATUS_ENGINE_1,
                departmentId = departmentId,
                startedAt = twoDaysAgo,
                completedAt = twoDaysAgo,
                startedByUserId = "admin-1",
                isFinalized = true,
                syncStatus = SyncStatus.SYNCED
            )
        )
        database.insertInspection(
            Inspection(
                id = "insp-seed-r1",
                templateId = DemoDepartmentSeeder.TEMPLATE_ENGINE,
                apparatusId = DemoDepartmentSeeder.APPARATUS_RESCUE_1,
                departmentId = departmentId,
                startedAt = oneHourAgo,
                completedAt = oneHourAgo,
                startedByUserId = "admin-1",
                isFinalized = true,
                syncStatus = SyncStatus.SYNCED
            )
        )
    }

    override fun getActiveTemplates(departmentId: String): Flow<List<InspectionTemplate>> = 
        _templates.asStateFlow().map { list -> list.filter { it.isActive && it.departmentId == departmentId } }

    override fun getTemplatesByApparatusType(departmentId: String, apparatusType: String): Flow<List<InspectionTemplate>> = 
        _templates.asStateFlow().map { list -> list.filter { it.apparatusType == apparatusType && it.isActive && it.departmentId == departmentId } }

    override suspend fun getTemplate(id: String): Result<InspectionTemplate> = 
        database.getAllTemplates().find { it.id == id }?.let { Result.success(it) } 
            ?: Result.failure(Exception("Template not found"))

    override suspend fun saveInspection(inspection: Inspection): Result<Unit> {
        database.insertInspection(inspection)
        refresh()
        return Result.success(Unit)
    }

    override fun getInspectionsForApparatus(apparatusId: String): Flow<List<Inspection>> = 
        _inspections.asStateFlow().map { list -> list.filter { it.apparatusId == apparatusId } }

    override suspend fun getLatestDraft(apparatusId: String): Result<Inspection?> {
        return Result.success(database.getLatestDraftByApparatus(apparatusId))
    }

    override fun getInspectionsByDepartment(departmentId: String): Flow<List<Inspection>> =
        _inspections.asStateFlow().map { list -> list.filter { it.departmentId == departmentId } }

    override suspend fun getLatestFinalizedInspection(apparatusId: String): Result<Inspection?> {
        return Result.success(database.getLatestFinalizedByApparatus(apparatusId))
    }

    override suspend fun getPendingSyncInspections(): Result<List<Inspection>> =
        Result.success(database.getPendingSyncInspections())

    override suspend fun updateSyncStatus(id: String, syncStatus: SyncStatus): Result<Unit> {
        database.updateInspectionSyncStatus(id, syncStatus)
        refresh()
        return Result.success(Unit)
    }
}
