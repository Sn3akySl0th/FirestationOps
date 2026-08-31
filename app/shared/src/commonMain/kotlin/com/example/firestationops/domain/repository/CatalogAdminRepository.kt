package com.example.firestationops.domain.repository

import com.example.firestationops.domain.catalog.ApparatusCatalogInput
import com.example.firestationops.domain.catalog.StationCatalogInput
import com.example.firestationops.domain.catalog.TemplateCatalogInput
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Station

interface CatalogAdminRepository {
    suspend fun upsertStation(
        actingMember: Member,
        input: StationCatalogInput,
        editingStationId: String? = null
    ): Result<Station>

    suspend fun upsertApparatus(
        actingMember: Member,
        input: ApparatusCatalogInput,
        editingApparatusId: String? = null
    ): Result<Apparatus>

    suspend fun upsertTemplate(
        actingMember: Member,
        input: TemplateCatalogInput,
        editingTemplateId: String? = null
    ): Result<InspectionTemplate>
}
