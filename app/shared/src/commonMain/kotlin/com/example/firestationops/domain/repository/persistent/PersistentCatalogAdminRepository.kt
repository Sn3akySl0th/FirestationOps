package com.example.firestationops.domain.repository.persistent

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.catalog.ApparatusCatalogInput
import com.example.firestationops.domain.catalog.CatalogAdminRules
import com.example.firestationops.domain.catalog.StationCatalogInput
import com.example.firestationops.domain.catalog.TemplateCatalogInput
import com.example.firestationops.domain.catalog.TemplateItemCatalogInput
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.InspectionTemplateItem
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Station
import com.example.firestationops.domain.repository.CatalogAdminRepository
import com.example.firestationops.randomUUID

class PersistentCatalogAdminRepository(
    private val database: FirestationOpsDatabase,
    private val onCatalogUpdated: () -> Unit
) : CatalogAdminRepository {
    fun notifyCatalogUpdated() {
        onCatalogUpdated()
    }

    override suspend fun upsertStation(
        actingMember: Member,
        input: StationCatalogInput,
        editingStationId: String?
    ): Result<Station> = runCatching {
        CatalogAdminRules.requireAdmin(actingMember)?.let { error(it) }
        val normalized = input.copy(
            name = input.name.trim(),
            address = input.address?.trim()?.takeIf { it.isNotEmpty() }
        )
        CatalogAdminRules.validateStationInput(normalized)?.let { error(it) }

        val now = currentTimeMillis()
        val existing = editingStationId?.let { id ->
            database.getAllStations().find { it.id == id && it.departmentId == actingMember.departmentId }
                ?: error("Station not found.")
        }

        val station = Station(
            id = existing?.id ?: "station-${randomUUID()}",
            departmentId = actingMember.departmentId,
            name = normalized.name,
            address = normalized.address,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        database.insertStation(station)
        onCatalogUpdated()
        station
    }

    override suspend fun upsertApparatus(
        actingMember: Member,
        input: ApparatusCatalogInput,
        editingApparatusId: String?
    ): Result<Apparatus> = runCatching {
        CatalogAdminRules.requireAdmin(actingMember)?.let { error(it) }

        val stations = database.getAllStations()
            .filter { it.departmentId == actingMember.departmentId }
        val normalized = input.copy(
            name = input.name.trim(),
            type = input.type.trim(),
            radioName = input.radioName.trim()
        )
        CatalogAdminRules.validateApparatusInput(normalized, stations)?.let { error(it) }

        val now = currentTimeMillis()
        val existing = editingApparatusId?.let { id ->
            database.getAllApparatus().find { it.id == id && it.departmentId == actingMember.departmentId }
                ?: error("Apparatus not found.")
        }

        val apparatus = Apparatus(
            id = existing?.id ?: "apparatus-${randomUUID()}",
            departmentId = actingMember.departmentId,
            stationId = normalized.stationId,
            name = normalized.name,
            type = normalized.type,
            radioName = normalized.radioName,
            status = normalized.status,
            year = existing?.year,
            make = existing?.make,
            model = existing?.model,
            vin = existing?.vin,
            licensePlate = existing?.licensePlate,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        database.insertApparatus(apparatus)
        onCatalogUpdated()
        apparatus
    }

    override suspend fun upsertTemplate(
        actingMember: Member,
        input: TemplateCatalogInput,
        editingTemplateId: String?
    ): Result<InspectionTemplate> = runCatching {
        CatalogAdminRules.requireAdmin(actingMember)?.let { error(it) }

        val normalized = input.copy(
            name = input.name.trim(),
            description = input.description?.trim()?.takeIf { it.isNotEmpty() },
            apparatusType = input.apparatusType.trim(),
            items = input.items.map { item ->
                item.copy(text = item.text.trim(), category = item.category?.trim()?.takeIf { it.isNotEmpty() })
            }
        )
        CatalogAdminRules.validateTemplateInput(normalized)?.let { error(it) }

        val departmentTemplates = database.getAllTemplates()
            .filter { it.departmentId == actingMember.departmentId }
        val now = currentTimeMillis()
        val existing = editingTemplateId?.let { id ->
            departmentTemplates.find { it.id == id } ?: error("Template not found.")
        }

        if (existing != null && !normalized.isActive) {
            if (!CatalogAdminRules.canDeactivateTemplate(existing.copy(isActive = false), departmentTemplates)) {
                error("Keep at least one active template for this apparatus type.")
            }
        }

        val items = normalized.items
            .filter { it.text.isNotBlank() }
            .map { item ->
                InspectionTemplateItem(
                    id = item.id ?: "item-${randomUUID()}",
                    text = item.text,
                    category = item.category,
                    isRequired = item.isRequired,
                    requiresNoteOnFail = item.requiresNoteOnFail
                )
            }

        val version = when {
            existing == null -> 1
            existing.items != items || existing.apparatusType != normalized.apparatusType -> existing.version + 1
            else -> existing.version
        }

        val template = InspectionTemplate(
            id = existing?.id ?: "template-${randomUUID()}",
            departmentId = actingMember.departmentId,
            name = normalized.name,
            description = normalized.description,
            apparatusType = normalized.apparatusType,
            version = version,
            frequencyHours = normalized.frequencyHours,
            isActive = normalized.isActive,
            items = items,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        database.insertTemplate(template)
        onCatalogUpdated()
        template
    }
}
