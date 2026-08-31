package com.example.firestationops.domain.bootstrap

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.InspectionTemplateItem
import com.example.firestationops.domain.model.Station

data class DepartmentCatalogProfile(
    val departmentName: String,
    val stations: List<StationDefinition>,
    val apparatus: List<ApparatusDefinition>,
    val templates: List<InspectionTemplateDefinition>,
    val retiredCanonicalIds: List<String> = emptyList()
) {
    val templateIds: List<String> get() = templates.map { it.id }
    val stationIds: List<String> get() = stations.map { it.id }
    val apparatusIds: List<String> get() = apparatus.map { it.id }

    fun allCanonicalIds(): List<String> = templateIds + stationIds + apparatusIds
}

data class StationDefinition(
    val id: String,
    val name: String,
    val address: String? = null
)

data class ApparatusDefinition(
    val id: String,
    val stationId: String,
    val name: String,
    val type: String,
    val radioName: String,
    val status: ApparatusStatus = ApparatusStatus.IN_SERVICE
)

data class InspectionTemplateDefinition(
    val id: String,
    val name: String,
    val apparatusType: String,
    val frequencyHours: Int,
    val items: List<InspectionTemplateItem>
)

object DepartmentCatalogProfiles {
    /** Fire department tenant ID (Calhoun VFD Department 5). */
    const val CALHOUN_DEPARTMENT_ID = "5"

    const val STATION_5 = "st-5"
    const val APPARATUS_ENGINE_5 = "ap-engine-5"
    const val APPARATUS_BRUSH_5 = "ap-brush-5"
    const val TEMPLATE_ENGINE = "tmpl-engine"
    const val TEMPLATE_BRUSH = "tmpl-brush"

    fun profileFor(departmentId: String): DepartmentCatalogProfile? = when (departmentId) {
        CALHOUN_DEPARTMENT_ID -> calhounDepartment5()
        else -> null
    }

    fun legacyCanonicalIdsFor(departmentId: String): List<String> {
        val profileIds = profileFor(departmentId)?.allCanonicalIds().orEmpty()
        val defaultIds = DemoDepartmentSeeder.defaultCanonicalIds()
        return (profileIds + defaultIds).distinct()
    }

    private fun calhounDepartment5(): DepartmentCatalogProfile = DepartmentCatalogProfile(
        departmentName = "Calhoun Fire Department - Department 5",
        stations = listOf(
            StationDefinition(
                id = STATION_5,
                name = "Department 5 Station",
                address = "Calhoun, TN"
            )
        ),
        templates = listOf(
            InspectionTemplateDefinition(
                id = TEMPLATE_ENGINE,
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
            InspectionTemplateDefinition(
                id = TEMPLATE_BRUSH,
                name = "Daily Brush Truck Inspection",
                apparatusType = "Brush",
                frequencyHours = 24,
                items = listOf(
                    InspectionTemplateItem(id = "b-1", text = "Engine Oil Level", category = "Engine"),
                    InspectionTemplateItem(id = "b-2", text = "Tire Pressure", category = "Exterior"),
                    InspectionTemplateItem(id = "b-3", text = "Lights and Siren", category = "Exterior"),
                    InspectionTemplateItem(id = "b-4", text = "Pump/Tank Level", category = "Pump")
                )
            )
        ),
        apparatus = listOf(
            ApparatusDefinition(
                id = APPARATUS_ENGINE_5,
                stationId = STATION_5,
                name = "Engine 5",
                type = "Engine",
                radioName = "E5"
            ),
            ApparatusDefinition(
                id = APPARATUS_BRUSH_5,
                stationId = STATION_5,
                name = "Brush 5",
                type = "Brush",
                radioName = "B5"
            )
        ),
        retiredCanonicalIds = listOf(
            DemoDepartmentSeeder.STATION_1,
            DemoDepartmentSeeder.STATION_2,
            DemoDepartmentSeeder.APPARATUS_ENGINE_1,
            DemoDepartmentSeeder.APPARATUS_LADDER_1,
            DemoDepartmentSeeder.APPARATUS_ENGINE_2,
            DemoDepartmentSeeder.APPARATUS_RESCUE_1,
            DemoDepartmentSeeder.TEMPLATE_LADDER
        )
    )
}

fun DepartmentCatalogProfile.toStation(departmentId: String, definition: StationDefinition): Station =
    Station(
        id = definition.id,
        departmentId = departmentId,
        name = definition.name,
        address = definition.address
    )

fun DepartmentCatalogProfile.toApparatus(departmentId: String, definition: ApparatusDefinition): Apparatus =
    Apparatus(
        id = definition.id,
        departmentId = departmentId,
        stationId = definition.stationId,
        name = definition.name,
        type = definition.type,
        radioName = definition.radioName,
        status = definition.status
    )

fun DepartmentCatalogProfile.toTemplate(departmentId: String, definition: InspectionTemplateDefinition): InspectionTemplate =
    InspectionTemplate(
        id = definition.id,
        departmentId = departmentId,
        name = definition.name,
        apparatusType = definition.apparatusType,
        frequencyHours = definition.frequencyHours,
        items = definition.items
    )
