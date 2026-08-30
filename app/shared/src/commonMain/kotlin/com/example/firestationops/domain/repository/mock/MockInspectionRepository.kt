package com.example.firestationops.domain.repository.mock

import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.InspectionTemplateItem
import com.example.firestationops.domain.repository.InspectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class MockInspectionRepository : InspectionRepository {
    private val templates = MutableStateFlow(
        listOf(
            InspectionTemplate(
                id = "tmpl-engine",
                departmentId = "mock-dept-id",
                name = "Daily Engine Inspection",
                apparatusType = "Engine",
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
                items = listOf(
                    InspectionTemplateItem(id = "l-1", text = "Hydraulic Fluid", category = "Aerial"),
                    InspectionTemplateItem(id = "l-2", text = "Ladder Extension", category = "Aerial"),
                    InspectionTemplateItem(id = "l-3", text = "Outriggers", category = "Aerial")
                )
            )
        )
    )

    override fun getActiveTemplates(departmentId: String): Flow<List<InspectionTemplate>> = 
        templates.map { list -> list.filter { it.isActive } }

    override fun getTemplatesByApparatusType(departmentId: String, apparatusType: String): Flow<List<InspectionTemplate>> = 
        templates.map { list -> list.filter { it.apparatusType == apparatusType && it.isActive } }

    override suspend fun getTemplate(id: String): Result<InspectionTemplate> = 
        templates.value.find { it.id == id }?.let { Result.success(it) } 
            ?: Result.failure(Exception("Template not found"))
}
