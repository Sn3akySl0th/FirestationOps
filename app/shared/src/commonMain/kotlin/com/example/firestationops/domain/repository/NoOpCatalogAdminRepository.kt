package com.example.firestationops.domain.repository

import com.example.firestationops.domain.catalog.ApparatusCatalogInput
import com.example.firestationops.domain.catalog.StationCatalogInput
import com.example.firestationops.domain.catalog.TemplateCatalogInput
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Station

class NoOpCatalogAdminRepository : CatalogAdminRepository {
    override suspend fun upsertStation(
        actingMember: Member,
        input: StationCatalogInput,
        editingStationId: String?
    ): Result<Station> = Result.failure(UnsupportedOperationException("Catalog admin is not available."))

    override suspend fun upsertApparatus(
        actingMember: Member,
        input: ApparatusCatalogInput,
        editingApparatusId: String?
    ): Result<Apparatus> = Result.failure(UnsupportedOperationException("Catalog admin is not available."))

    override suspend fun upsertTemplate(
        actingMember: Member,
        input: TemplateCatalogInput,
        editingTemplateId: String?
    ): Result<InspectionTemplate> = Result.failure(UnsupportedOperationException("Catalog admin is not available."))
}
