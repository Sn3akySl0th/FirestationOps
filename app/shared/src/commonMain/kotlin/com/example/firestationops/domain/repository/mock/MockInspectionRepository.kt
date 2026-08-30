package com.example.firestationops.domain.repository.mock

import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.InspectionTemplateItem
import com.example.firestationops.domain.repository.InspectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class MockInspectionRepository : InspectionRepository {
    private val templates = MutableStateFlow(
        listOf(
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
            ),
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
    )

    private val inspections = MutableStateFlow<List<Inspection>>(emptyList())

    override fun getActiveTemplates(departmentId: String): Flow<List<InspectionTemplate>> = 
        templates.map { list -> list.filter { it.isActive } }

    override fun getTemplatesByApparatusType(departmentId: String, apparatusType: String): Flow<List<InspectionTemplate>> = 
        templates.map { list -> list.filter { it.apparatusType == apparatusType && it.isActive } }

    override suspend fun getTemplate(id: String): Result<InspectionTemplate> = 
        templates.value.find { it.id == id }?.let { Result.success(it) } 
            ?: Result.failure(Exception("Template not found"))

    override suspend fun saveInspection(inspection: Inspection): Result<Unit> {
        inspections.update { (it.filter { i -> i.id != inspection.id } + inspection) }
        return Result.success(Unit)
    }

    override fun getInspectionsForApparatus(apparatusId: String): Flow<List<Inspection>> = 
        inspections.map { list -> list.filter { it.apparatusId == apparatusId } }

    override suspend fun getLatestDraft(apparatusId: String): Result<Inspection?> {
        val draft = inspections.value
            .filter { it.apparatusId == apparatusId && !it.isFinalized }
            .maxByOrNull { it.startedAt }
        return Result.success(draft)
    }

    override fun getInspectionsByDepartment(departmentId: String): Flow<List<Inspection>> =
        inspections.map { list -> list.filter { it.departmentId == departmentId } }

    override suspend fun getLatestFinalizedInspection(apparatusId: String): Result<Inspection?> {
        val latest = inspections.value
            .filter { it.apparatusId == apparatusId && it.isFinalized && it.completedAt != null }
            .maxByOrNull { it.completedAt!! }
        return Result.success(latest)
    }
}
