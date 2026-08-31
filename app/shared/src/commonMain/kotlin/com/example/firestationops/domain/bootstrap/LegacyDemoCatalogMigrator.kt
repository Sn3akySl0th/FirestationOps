package com.example.firestationops.domain.bootstrap

import com.example.firestationops.domain.membership.CalhounMembershipNormalizer

object LegacyDemoCatalogMigrator {
    fun legacyEntityId(departmentId: String, canonicalId: String): String = "$departmentId-$canonicalId"

    fun legacyDemoIdPairs(departmentId: String): List<Pair<String, String>> {
        if (departmentId.isBlank()) return emptyList()

        val catalogDepartmentId = resolveCatalogDepartmentId(departmentId)
        return DepartmentCatalogProfiles.legacyCanonicalIdsFor(catalogDepartmentId).map { canonicalId ->
            legacyEntityId(departmentId, canonicalId) to canonicalId
        }
    }

    /**
     * Badge numbers (200–225) were briefly stored as [departmentId]; catalog data lives under the
     * real fire department number instead.
     */
    fun resolveCatalogDepartmentId(departmentId: String): String =
        if (CalhounMembershipNormalizer.isLegacyMemberNumberUsedAsDepartmentId(departmentId)) {
            CalhounMembershipNormalizer.DEPARTMENT_NUMBER
        } else {
            departmentId
        }
}
