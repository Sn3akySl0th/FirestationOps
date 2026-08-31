package com.example.firestationops.domain.bootstrap

import com.example.firestationops.domain.model.Member

class NoOpDepartmentCatalogBootstrap : DepartmentCatalogBootstrap {
    override suspend fun isCloudCatalogEmpty(departmentId: String): Boolean = false

    override suspend fun bootstrapDemoCatalog(departmentId: String, member: Member): Result<Int> =
        Result.failure(UnsupportedOperationException("Cloud catalog bootstrap is not available on this platform."))
}
