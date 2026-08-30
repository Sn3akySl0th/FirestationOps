package com.example.firestationops.domain.bootstrap

object LegacyDemoCatalogMigrator {
    fun legacyEntityId(departmentId: String, canonicalId: String): String = "$departmentId-$canonicalId"

    fun legacyDemoIdPairs(departmentId: String): List<Pair<String, String>> {
        if (departmentId.isBlank()) return emptyList()

        val canonicalIds = listOf(
            DemoDepartmentSeeder.TEMPLATE_ENGINE,
            DemoDepartmentSeeder.TEMPLATE_LADDER,
            DemoDepartmentSeeder.STATION_1,
            DemoDepartmentSeeder.STATION_2,
            DemoDepartmentSeeder.APPARATUS_ENGINE_1,
            DemoDepartmentSeeder.APPARATUS_LADDER_1,
            DemoDepartmentSeeder.APPARATUS_ENGINE_2,
            DemoDepartmentSeeder.APPARATUS_RESCUE_1
        )

        return canonicalIds.map { canonicalId ->
            legacyEntityId(departmentId, canonicalId) to canonicalId
        }
    }
}
