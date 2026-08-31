package com.example.firestationops.data.firebase

import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.catalog.ApparatusCatalogInput
import com.example.firestationops.domain.catalog.StationCatalogInput
import com.example.firestationops.domain.catalog.TemplateCatalogInput
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Station
import com.example.firestationops.domain.repository.CatalogAdminRepository
import com.example.firestationops.domain.repository.persistent.PersistentCatalogAdminRepository

class JvmFirebaseCatalogAdminRepository(
    private val local: PersistentCatalogAdminRepository,
    private val database: FirestationOpsDatabase,
    private val firebaseEnabled: Boolean
) : CatalogAdminRepository {
    override suspend fun upsertStation(
        actingMember: Member,
        input: StationCatalogInput,
        editingStationId: String?
    ): Result<Station> {
        val previousStation = editingStationId?.let { id ->
            database.getAllStations().find { it.id == id }
        }
        val localResult = local.upsertStation(actingMember, input, editingStationId)
        if (localResult.isFailure || !firebaseEnabled) {
            return localResult
        }

        val station = localResult.getOrThrow()
        return runCatching {
            JvmGoogleFirestoreClient.setDocument(
                FirestorePaths.station(station.departmentId, station.id),
                FirestoreMappers.stationToMap(station),
                merge = true
            )
            station
        }.recoverCatching { error ->
            rollbackStation(station, previousStation, editingStationId)
            throw mapCloudError(error)
        }
    }

    override suspend fun upsertApparatus(
        actingMember: Member,
        input: ApparatusCatalogInput,
        editingApparatusId: String?
    ): Result<Apparatus> {
        val previousApparatus = editingApparatusId?.let { id ->
            database.getAllApparatus().find { it.id == id }
        }
        val localResult = local.upsertApparatus(actingMember, input, editingApparatusId)
        if (localResult.isFailure || !firebaseEnabled) {
            return localResult
        }

        val apparatus = localResult.getOrThrow()
        return runCatching {
            JvmGoogleFirestoreClient.setDocument(
                FirestorePaths.apparatus(apparatus.departmentId, apparatus.id),
                FirestoreMappers.apparatusToMap(apparatus),
                merge = true
            )
            apparatus
        }.recoverCatching { error ->
            rollbackApparatus(apparatus, previousApparatus, editingApparatusId)
            throw mapCloudError(error)
        }
    }

    override suspend fun upsertTemplate(
        actingMember: Member,
        input: TemplateCatalogInput,
        editingTemplateId: String?
    ): Result<InspectionTemplate> {
        val previousTemplate = editingTemplateId?.let { id ->
            database.getAllTemplates().find { it.id == id }
        }
        val localResult = local.upsertTemplate(actingMember, input, editingTemplateId)
        if (localResult.isFailure || !firebaseEnabled) {
            return localResult
        }

        val template = localResult.getOrThrow()
        return runCatching {
            JvmGoogleFirestoreClient.setDocument(
                FirestorePaths.template(template.departmentId, template.id),
                FirestoreMappers.templateToMap(template),
                merge = true
            )
            template
        }.recoverCatching { error ->
            rollbackTemplate(template, previousTemplate, editingTemplateId)
            throw mapCloudError(error)
        }
    }

    private fun rollbackStation(saved: Station, previous: Station?, editingId: String?) {
        if (editingId == null) {
            database.deleteStationById(saved.id)
        } else if (previous != null) {
            database.insertStation(previous)
        }
        local.notifyCatalogUpdated()
    }

    private fun rollbackApparatus(saved: Apparatus, previous: Apparatus?, editingId: String?) {
        if (editingId == null) {
            database.deleteApparatusById(saved.id)
        } else if (previous != null) {
            database.insertApparatus(previous)
        }
        local.notifyCatalogUpdated()
    }

    private fun rollbackTemplate(saved: InspectionTemplate, previous: InspectionTemplate?, editingId: String?) {
        if (editingId == null) {
            database.deleteTemplateById(saved.id)
        } else if (previous != null) {
            database.insertTemplate(previous)
        }
        local.notifyCatalogUpdated()
    }

    private fun mapCloudError(error: Throwable): Throwable {
        if (error.message?.contains("PERMISSION_DENIED", ignoreCase = true) == true) {
            return IllegalStateException("Insufficient permissions to update the department catalog.")
        }
        return error
    }
}
