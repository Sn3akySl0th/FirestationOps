package com.example.firestationops.domain.bootstrap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyDemoCatalogMigratorTest {
    @Test
    fun legacyEntityId_prefixesCanonicalIdWithDepartment() {
        assertEquals(
            "221-ap-engine-1",
            LegacyDemoCatalogMigrator.legacyEntityId("221", DemoDepartmentSeeder.APPARATUS_ENGINE_1)
        )
    }

    @Test
    fun legacyDemoIdPairs_mapsAllCanonicalDemoEntities() {
        val pairs = LegacyDemoCatalogMigrator.legacyDemoIdPairs("221")

        assertTrue(pairs.size >= 8)
        assertTrue(pairs.contains("221-st-5" to DepartmentCatalogProfiles.STATION_5))
        assertTrue(pairs.contains("221-ap-engine-5" to DepartmentCatalogProfiles.APPARATUS_ENGINE_5))
        assertTrue(pairs.contains("221-tmpl-engine" to DepartmentCatalogProfiles.TEMPLATE_ENGINE))
    }
}
