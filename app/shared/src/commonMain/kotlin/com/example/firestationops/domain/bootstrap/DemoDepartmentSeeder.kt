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

    fun defaultCanonicalIds(): List<String> = listOf(
        TEMPLATE_ENGINE,
        TEMPLATE_LADDER,
        STATION_1,
        STATION_2,
        APPARATUS_ENGINE_1,
        APPARATUS_LADDER_1,
        APPARATUS_ENGINE_2,
        APPARATUS_RESCUE_1
    )

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

        removeLegacyPrefixedDemoCatalog(database, departmentId)

        val profile = DepartmentCatalogProfiles.profileFor(departmentId)
        if (profile != null) {
            applyProfile(database, departmentId, profile)
            return
        }

        ensureDepartmentRecord(database, departmentId, "Department $departmentId")

        if (!hasDefaultDemoCatalog(database)) {
            seedDefaultTemplates(database, departmentId)
            seedDefaultStationsAndApparatus(database, departmentId)
        } else {
            reassignDefaultDemoCatalogToDepartment(database, departmentId)
        }
    }

    private fun applyProfile(
        database: FirestationOpsDatabase,
        departmentId: String,
        profile: DepartmentCatalogProfile
    ) {
        val now = currentTimeMillis()
        database.insertDepartment(
            Department(
                id = departmentId,
                name = profile.departmentName,
                stationIds = profile.stationIds,
                createdAt = now,
                updatedAt = now
            )
        )

        profile.templates.forEach { templateDefinition ->
            database.insertTemplate(profile.toTemplate(departmentId, templateDefinition))
        }
        profile.stations.forEach { stationDefinition ->
            database.insertStation(profile.toStation(departmentId, stationDefinition))
        }
        profile.apparatus.forEach { apparatusDefinition ->
            database.insertApparatus(profile.toApparatus(departmentId, apparatusDefinition))
        }

        retireReplacedCatalogEntities(database, profile)
    }

    private fun retireReplacedCatalogEntities(
        database: FirestationOpsDatabase,
        profile: DepartmentCatalogProfile
    ) {
        val primaryEngineId = profile.apparatus.firstOrNull { it.type == "Engine" }?.id
        val replacements = mapOf(
            APPARATUS_ENGINE_1 to (primaryEngineId ?: profile.apparatusIds.firstOrNull()),
            APPARATUS_ENGINE_2 to primaryEngineId,
            APPARATUS_LADDER_1 to primaryEngineId,
            APPARATUS_RESCUE_1 to primaryEngineId
        )

        replacements.forEach { (oldId, newId) ->
            if (newId == null || oldId == newId) return@forEach
            database.updateInspectionApparatusId(newId, oldId)
            database.updateDeficiencyApparatusId(newId, oldId)
            database.updateUnitAssignmentApparatusId(newId, oldId)
        }

        profile.retiredCanonicalIds.forEach { retiredId ->
            when (retiredId) {
                in demoTemplateIds -> database.deleteTemplateById(retiredId)
                in demoApparatusIds -> database.deleteApparatusById(retiredId)
                in demoStationIds -> database.deleteStationById(retiredId)
            }
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

    private fun ensureDepartmentRecord(
        database: FirestationOpsDatabase,
        departmentId: String,
        departmentName: String
    ) {
        val existing = database.getDepartmentById(departmentId)
        val now = currentTimeMillis()
        database.insertDepartment(
            Department(
                id = departmentId,
                name = departmentName,
                stationIds = existing?.stationIds ?: emptyList(),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
    }

    private fun hasDefaultDemoCatalog(database: FirestationOpsDatabase): Boolean =
        database.getAllTemplates().any { it.id == TEMPLATE_ENGINE }

    private fun reassignDefaultDemoCatalogToDepartment(database: FirestationOpsDatabase, departmentId: String) {
        demoTemplateIds.forEach { database.updateTemplateDepartmentId(it, departmentId) }
        demoStationIds.forEach { database.updateStationDepartmentId(it, departmentId) }
        demoApparatusIds.forEach { database.updateApparatusDepartmentId(it, departmentId) }
        ensureDepartmentRecord(database, departmentId, "Department $departmentId")
    }

    private fun seedDefaultTemplates(database: FirestationOpsDatabase, departmentId: String) {
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

    private fun seedDefaultStationsAndApparatus(database: FirestationOpsDatabase, departmentId: String) {
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
