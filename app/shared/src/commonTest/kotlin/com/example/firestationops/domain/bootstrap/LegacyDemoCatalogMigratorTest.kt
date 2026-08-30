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

        assertEquals(8, pairs.size)
        assertTrue(pairs.contains("221-st-1" to DemoDepartmentSeeder.STATION_1))
        assertTrue(pairs.contains("221-ap-engine-1" to DemoDepartmentSeeder.APPARATUS_ENGINE_1))
        assertTrue(pairs.contains("221-tmpl-engine" to DemoDepartmentSeeder.TEMPLATE_ENGINE))
    }
}
