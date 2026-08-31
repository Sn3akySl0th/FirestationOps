package com.example.firestationops.domain.repository.persistent

import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.Department
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Station
import com.example.firestationops.domain.repository.CatalogRepository

class PersistentCatalogRepository(
    private val database: FirestationOpsDatabase,
    private val onCatalogUpdated: () -> Unit
) : CatalogRepository {
    override suspend fun applyDepartment(department: Department): Result<Unit> = runCatching {
        database.insertDepartment(department)
    }

    override suspend fun applyStation(station: Station): Result<Unit> = runCatching {
        database.insertStation(station)
    }

    override suspend fun applyApparatus(apparatus: Apparatus): Result<Unit> = runCatching {
        database.insertApparatus(apparatus)
    }

    override suspend fun applyTemplate(template: InspectionTemplate): Result<Unit> = runCatching {
        database.insertTemplate(template)
    }

    override suspend fun applyMember(member: Member): Result<Unit> = runCatching {
        database.insertMember(member)
    }

    override fun notifyCatalogUpdated() {
        onCatalogUpdated()
    }
}
