package com.example.firestationops.domain.repository.persistent

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.db.FirestationOpsDatabase
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
        val templates = database.getAllTemplates()
        if (templates.isEmpty()) {
            seed()
        }
        refresh()
    }

    private fun refresh() {
        _templates.value = database.getAllTemplates()
        _inspections.value = database.getAllInspections()
    }

    private fun seed() {
        database.insertTemplate(
            InspectionTemplate(
                id = "tmpl-engine",
                departmentId = "mock-dept-id",
                name = "Daily Engine Inspection",
                apparatusType = "Engine",
                frequencyHours = 24,
                items = listOf(
                    InspectionTemplateItem(id = "item-1", text = "Engine Oil Level", category = "Engine"),
                    InspectionTemplateItem(id = "item-2", text = "Coolant Level", category = "Engine"),
                    InspectionTemplateItem(id = "item-3", text = "Tire Pressure", category = "Exterior"),
                    InspectionTemplateItem(id = "item-4", text = "Lights and Siren", category = "Exterior"),
                    InspectionTemplateItem(id = "item-5", text = "Pump Engagement", category = "Pump")
                )
            )
        )
        database.insertTemplate(
            InspectionTemplate(
                id = "tmpl-ladder",
                departmentId = "mock-dept-id",
                name = "Weekly Ladder Inspection",
                apparatusType = "Ladder",
                frequencyHours = 168,
                items = listOf(
                    InspectionTemplateItem(id = "l-1", text = "Hydraulic Fluid", category = "Aerial"),
                    InspectionTemplateItem(id = "l-2", text = "Ladder Extension", category = "Aerial"),
                    InspectionTemplateItem(id = "l-3", text = "Outriggers", category = "Aerial")
                )
            )
        )

        seedDashboardDemoInspections()
    }

    private fun seedDashboardDemoInspections() {
        if (database.getAllInspections().isNotEmpty()) return

        val now = currentTimeMillis()
        val twoDaysAgo = now - (2 * 86_400_000L)
        val oneHourAgo = now - (3_600_000L)

        database.insertInspection(
            Inspection(
                id = "insp-seed-e1",
                templateId = "tmpl-engine",
                apparatusId = "ap-1",
                departmentId = "mock-dept-id",
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
                templateId = "tmpl-engine",
                apparatusId = "ap-4",
                departmentId = "mock-dept-id",
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
}
