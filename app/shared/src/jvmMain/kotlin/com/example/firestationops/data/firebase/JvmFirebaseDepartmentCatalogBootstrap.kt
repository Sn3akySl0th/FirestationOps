package com.example.firestationops.data.firebase

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.bootstrap.DemoDepartmentSeeder
import com.example.firestationops.domain.bootstrap.DepartmentCatalogBootstrap
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role

class JvmFirebaseDepartmentCatalogBootstrap(
    private val firebaseEnabled: Boolean,
    private val database: FirestationOpsDatabase
) : DepartmentCatalogBootstrap {
    override suspend fun isCloudCatalogEmpty(departmentId: String): Boolean {
        if (!firebaseEnabled || departmentId.isBlank()) return false

        val snapshot = JvmGoogleFirestoreClient.listCollection(
            "${FirestorePaths.department(departmentId)}/stations"
        )

        return snapshot.isEmpty()
    }

    override suspend fun bootstrapDemoCatalog(departmentId: String, member: Member): Result<Int> {
        if (!firebaseEnabled) {
            return Result.failure(IllegalStateException("Firebase is not configured on this device."))
        }
        if (!member.hasRole(Role.ADMIN)) {
            return Result.failure(IllegalStateException("Only administrators can bootstrap the department catalog."))
        }
        if (departmentId.isBlank()) {
            return Result.failure(IllegalStateException("Department ID is required."))
        }

        return runCatching {
            if (!isCloudCatalogEmpty(departmentId)) {
                error("Department catalog already exists in the cloud.")
            }

            DemoDepartmentSeeder.ensureDemoData(database, departmentId)
            val now = currentTimeMillis()
            val department = database.getDepartmentById(departmentId)
                ?: error("Local department record is missing.")

            JvmGoogleFirestoreClient.setDocument(
                FirestorePaths.department(departmentId),
                FirestoreMappers.departmentToMap(department),
                merge = true
            )

            var uploadedCount = 1

            database.getAllStations()
                .filter { it.departmentId == departmentId }
                .forEach { station ->
                    JvmGoogleFirestoreClient.setDocument(
                        FirestorePaths.station(departmentId, station.id),
                        FirestoreMappers.stationToMap(station.copy(updatedAt = now)),
                        merge = true
                    )
                    uploadedCount++
                }

            database.getAllApparatus()
                .filter { it.departmentId == departmentId }
                .forEach { apparatus ->
                    JvmGoogleFirestoreClient.setDocument(
                        FirestorePaths.apparatus(departmentId, apparatus.id),
                        FirestoreMappers.apparatusToMap(apparatus.copy(updatedAt = now)),
                        merge = true
                    )
                    uploadedCount++
                }

            database.getAllTemplates()
                .filter { it.departmentId == departmentId }
                .forEach { template ->
                    JvmGoogleFirestoreClient.setDocument(
                        FirestorePaths.template(departmentId, template.id),
                        FirestoreMappers.templateToMap(template.copy(updatedAt = now)),
                        merge = true
                    )
                    uploadedCount++
                }

            uploadedCount
        }
    }
}
