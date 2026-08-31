package com.example.firestationops.data.firebase

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.bootstrap.DemoDepartmentSeeder
import com.example.firestationops.domain.bootstrap.DepartmentCatalogBootstrap
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirebaseDepartmentCatalogBootstrap(
    private val firebaseEnabled: Boolean,
    private val database: FirestationOpsDatabase,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : DepartmentCatalogBootstrap {
    override suspend fun isCloudCatalogEmpty(departmentId: String): Boolean {
        if (!firebaseEnabled || departmentId.isBlank()) return false

        val snapshot = firestore.collection("departments")
            .document(departmentId)
            .collection("stations")
            .limit(1)
            .get()
            .await()

        return snapshot.isEmpty
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

            firestore.document(FirestorePaths.department(departmentId))
                .set(FirestoreMappers.departmentToMap(department), SetOptions.merge())
                .await()

            var uploadedCount = 1

            database.getAllStations()
                .filter { it.departmentId == departmentId }
                .forEach { station ->
                    firestore.document(FirestorePaths.station(departmentId, station.id))
                        .set(FirestoreMappers.stationToMap(station.copy(updatedAt = now)), SetOptions.merge())
                        .await()
                    uploadedCount++
                }

            database.getAllApparatus()
                .filter { it.departmentId == departmentId }
                .forEach { apparatus ->
                    firestore.document(FirestorePaths.apparatus(departmentId, apparatus.id))
                        .set(FirestoreMappers.apparatusToMap(apparatus.copy(updatedAt = now)), SetOptions.merge())
                        .await()
                    uploadedCount++
                }

            database.getAllTemplates()
                .filter { it.departmentId == departmentId }
                .forEach { template ->
                    firestore.document(FirestorePaths.template(departmentId, template.id))
                        .set(FirestoreMappers.templateToMap(template.copy(updatedAt = now)), SetOptions.merge())
                        .await()
                    uploadedCount++
                }

            uploadedCount
        }
    }
}
