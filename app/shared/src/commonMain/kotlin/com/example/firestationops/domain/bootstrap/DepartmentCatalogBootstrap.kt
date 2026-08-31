package com.example.firestationops.domain.bootstrap

import com.example.firestationops.domain.model.Member

interface DepartmentCatalogBootstrap {
    suspend fun isCloudCatalogEmpty(departmentId: String): Boolean
    suspend fun bootstrapDemoCatalog(departmentId: String, member: Member): Result<Int>
}
