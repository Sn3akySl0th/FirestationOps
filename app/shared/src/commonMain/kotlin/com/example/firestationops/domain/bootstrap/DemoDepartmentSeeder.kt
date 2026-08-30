package com.example.firestationops.domain.bootstrap

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.Department
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.InspectionTemplateItem
import com.example.firestationops.domain.model.Station

object DemoDepartmentSeeder {
    const val TEMPLATE_ENGINE = "tmpl-engine"
    const val TEMPLATE_LADDER = "tmpl-ladder"
    const val STATION_1 = "st-1"
    const val STATION_2 = "st-2"
    const val APPARATUS_ENGINE_1 = "ap-engine-1"
    const val APPARATUS_LADDER_1 = "ap-ladder-1"
    const val APPARATUS_ENGINE_2 = "ap-engine-2"
    const val APPARATUS_RESCUE_1 = "ap-rescue-1"

    private val demoTemplateIds = listOf(TEMPLATE_ENGINE, TEMPLATE_LADDER)
    private val demoStationIds = listOf(STATION_1, STATION_2)
    private val demoApparatusIds = listOf(
        APPARATUS_ENGINE_1,
        APPARATUS_LADDER_1,
        APPARATUS_ENGINE_2,
        APPARATUS_RESCUE_1
    )

    fun ensureDemoData(database: FirestationOpsDatabase, departmentId: String) {
        if (departmentId.isBlank()) return

        ensureDepartmentRecord(database, departmentId)
        removeLegacyPrefixedDemoCatalog(database, departmentId)

        if (!hasDemoCatalog(database)) {
            seedTemplates(database, departmentId)
            seedStationsAndApparatus(database, departmentId)
        } else {
            reassignDemoCatalogToDepartment(database, departmentId)
        }
    }

    private fun removeLegacyPrefixedDemoCatalog(database: FirestationOpsDatabase, departmentId: String) {
        val templateIds = database.getAllTemplates().map { it.id }.toSet()
        val stationIds = database.getAllStations().map { it.id }.toSet()
        val apparatusIds = database.getAllApparatus().map { it.id }.toSet()

        for ((legacyId, canonicalId) in LegacyDemoCatalogMigrator.legacyDemoIdPairs(departmentId)) {
            if (legacyId == canonicalId || legacyId !in templateIds && legacyId !in stationIds && legacyId !in apparatusIds) {
                continue
            }

            when {
                legacyId in templateIds -> {
                    database.updateInspectionTemplateId(canonicalId, legacyId)
                    database.deleteTemplateById(legacyId)
                }
                legacyId in apparatusIds -> {
                    database.updateInspectionApparatusId(canonicalId, legacyId)
                    database.updateDeficiencyApparatusId(canonicalId, legacyId)
                    database.updateUnitAssignmentApparatusId(canonicalId, legacyId)
                    database.deleteApparatusById(legacyId)
                }
                legacyId in stationIds -> {
                    database.updateApparatusStationId(canonicalId, legacyId)
                    database.deleteStationById(legacyId)
                }
            }
        }
    }

    private fun ensureDepartmentRecord(database: FirestationOpsDatabase, departmentId: String) {
        if (database.getDepartmentById(departmentId) != null) return

        val now = currentTimeMillis()
        database.insertDepartment(
            Department(
                id = departmentId,
                name = "Department $departmentId",
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun hasDemoCatalog(database: FirestationOpsDatabase): Boolean =
        database.getAllTemplates().any { it.id == TEMPLATE_ENGINE }

    private fun reassignDemoCatalogToDepartment(database: FirestationOpsDatabase, departmentId: String) {
        demoTemplateIds.forEach { database.updateTemplateDepartmentId(it, departmentId) }
        demoStationIds.forEach { database.updateStationDepartmentId(it, departmentId) }
        demoApparatusIds.forEach { database.updateApparatusDepartmentId(it, departmentId) }
    }

    private fun seedTemplates(database: FirestationOpsDatabase, departmentId: String) {
        database.insertTemplate(
            InspectionTemplate(
                id = TEMPLATE_ENGINE,
                departmentId = departmentId,
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
                id = TEMPLATE_LADDER,
                departmentId = departmentId,
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
    }

    private fun seedStationsAndApparatus(database: FirestationOpsDatabase, departmentId: String) {
        database.insertStation(
            Station(
                id = STATION_1,
                departmentId = departmentId,
                name = "Station 1",
                address = "123 Main St"
            )
        )
        database.insertStation(
            Station(
                id = STATION_2,
                departmentId = departmentId,
                name = "Station 2",
                address = "456 Oak St"
            )
        )

        database.insertApparatus(
            Apparatus(
                id = APPARATUS_ENGINE_1,
                departmentId = departmentId,
                stationId = STATION_1,
                name = "Engine 1",
                type = "Engine",
                radioName = "E1",
                status = ApparatusStatus.IN_SERVICE
            )
        )
        database.insertApparatus(
            Apparatus(
                id = APPARATUS_LADDER_1,
                departmentId = departmentId,
                stationId = STATION_1,
                name = "Ladder 1",
                type = "Ladder",
                radioName = "L1",
                status = ApparatusStatus.IN_SERVICE
            )
        )
        database.insertApparatus(
            Apparatus(
                id = APPARATUS_ENGINE_2,
                departmentId = departmentId,
                stationId = STATION_2,
                name = "Engine 2",
                type = "Engine",
                radioName = "E2",
                status = ApparatusStatus.OUT_OF_SERVICE
            )
        )
        database.insertApparatus(
            Apparatus(
                id = APPARATUS_RESCUE_1,
                departmentId = departmentId,
                stationId = STATION_2,
                name = "Rescue 1",
                type = "Rescue",
                radioName = "R1",
                status = ApparatusStatus.IN_SERVICE
            )
        )
    }
}
