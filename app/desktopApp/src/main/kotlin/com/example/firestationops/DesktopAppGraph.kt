package com.example.firestationops

import com.example.firestationops.data.firebase.JvmFirebaseAuthRepository
import com.example.firestationops.data.firebase.JvmFirebaseBootstrap
import com.example.firestationops.data.firebase.JvmFirebaseCatalogAdminRepository
import com.example.firestationops.data.firebase.JvmFirebaseDepartmentCatalogBootstrap
import com.example.firestationops.data.firebase.JvmFirebaseSyncCoordinator
import com.example.firestationops.data.firebase.JvmSyncAttachmentCache
import com.example.firestationops.db.DatabaseDriverFactory
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.bootstrap.DepartmentCatalogBootstrap
import com.example.firestationops.domain.bootstrap.DemoDepartmentSeeder
import com.example.firestationops.domain.bootstrap.DepartmentCatalogProfiles
import com.example.firestationops.domain.bootstrap.NoOpDepartmentCatalogBootstrap
import com.example.firestationops.domain.repository.ApparatusRepository
import com.example.firestationops.domain.repository.AttachmentRepository
import com.example.firestationops.domain.repository.AuthRepository
import com.example.firestationops.domain.repository.CatalogAdminRepository
import com.example.firestationops.domain.repository.CatalogRepository
import com.example.firestationops.domain.repository.DeficiencyRepository
import com.example.firestationops.domain.repository.DepartmentRepository
import com.example.firestationops.domain.repository.IncidentRepository
import com.example.firestationops.domain.repository.InspectionRepository
import com.example.firestationops.domain.repository.MemberRosterRepository
import com.example.firestationops.domain.repository.NoOpMemberRosterRepository
import com.example.firestationops.domain.repository.persistent.PersistentApparatusRepository
import com.example.firestationops.domain.repository.persistent.PersistentAttachmentRepository
import com.example.firestationops.domain.repository.persistent.PersistentAuthRepository
import com.example.firestationops.domain.repository.persistent.PersistentCatalogAdminRepository
import com.example.firestationops.domain.repository.persistent.PersistentCatalogRepository
import com.example.firestationops.domain.repository.persistent.PersistentDeficiencyRepository
import com.example.firestationops.domain.repository.persistent.PersistentDepartmentRepository
import com.example.firestationops.domain.repository.persistent.PersistentIncidentRepository
import com.example.firestationops.domain.repository.persistent.PersistentInspectionRepository
import com.example.firestationops.domain.repository.persistent.PersistentMemberRosterRepository
import com.example.firestationops.domain.sync.NoOpSyncCoordinator
import com.example.firestationops.data.sync.SyncAttachmentCache
import com.example.firestationops.domain.sync.SyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DesktopAppGraph {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: FirestationOpsDatabase = FirestationOpsDatabase(
        DatabaseDriverFactory().createDriver()
    )

    val firebaseEnabled: Boolean = JvmFirebaseBootstrap.initializeIfConfigured()

    val apparatusRepository: ApparatusRepository = PersistentApparatusRepository(database)
    val inspectionRepository: InspectionRepository = PersistentInspectionRepository(database)
    val deficiencyRepository: DeficiencyRepository = PersistentDeficiencyRepository(database)
    val attachmentRepository: AttachmentRepository = PersistentAttachmentRepository(database)
    val syncAttachmentCache: SyncAttachmentCache = JvmSyncAttachmentCache()
    val incidentRepository: IncidentRepository = PersistentIncidentRepository(database)
    val departmentRepository: DepartmentRepository = PersistentDepartmentRepository(database)

    private val localAuthRepository: PersistentAuthRepository = PersistentAuthRepository(database)
    val memberRosterRepository: MemberRosterRepository = if (firebaseEnabled) {
        NoOpMemberRosterRepository(
            "Cloud roster management is currently available on Android only. " +
                "Use an Android administrator device so membership changes go through the secure member service."
        )
    } else {
        PersistentMemberRosterRepository(database)
    }

    val catalogRepository: CatalogRepository = PersistentCatalogRepository(database) {
        (apparatusRepository as PersistentApparatusRepository).refreshCatalog()
        (inspectionRepository as PersistentInspectionRepository).refreshCatalog()
    }

    private val localCatalogAdminRepository = PersistentCatalogAdminRepository(database) {
        (apparatusRepository as PersistentApparatusRepository).refreshCatalog()
        (inspectionRepository as PersistentInspectionRepository).refreshCatalog()
    }

    val catalogAdminRepository: CatalogAdminRepository = if (firebaseEnabled) {
        JvmFirebaseCatalogAdminRepository(
            local = localCatalogAdminRepository,
            database = database,
            firebaseEnabled = true
        )
    } else {
        localCatalogAdminRepository
    }

    val departmentCatalogBootstrap: DepartmentCatalogBootstrap = if (firebaseEnabled) {
        JvmFirebaseDepartmentCatalogBootstrap(
            firebaseEnabled = true,
            database = database
        )
    } else {
        NoOpDepartmentCatalogBootstrap()
    }

    val authRepository: AuthRepository = if (firebaseEnabled) {
        JvmFirebaseAuthRepository(
            database = database,
            localAuth = localAuthRepository,
            firebaseEnabled = true
        )
    } else {
        localAuthRepository
    }

    val syncConflictRepository: com.example.firestationops.domain.repository.SyncConflictRepository =
        com.example.firestationops.domain.repository.persistent.PersistentSyncConflictRepository(database)

    val syncCoordinator: SyncCoordinator = if (firebaseEnabled) {
        JvmFirebaseSyncCoordinator(
            firebaseEnabled = true,
            attachmentCache = syncAttachmentCache,
            catalogRepository = catalogRepository,
            attachmentRepository = attachmentRepository,
            inspectionRepository = inspectionRepository,
            deficiencyRepository = deficiencyRepository,
            incidentRepository = incidentRepository,
            syncConflictRepository = syncConflictRepository
        )
    } else {
        NoOpSyncCoordinator()
    }

    fun prepareDepartment(departmentId: String) {
        when {
            DepartmentCatalogProfiles.profileFor(departmentId) != null -> {
                DemoDepartmentSeeder.ensureDemoData(database, departmentId)
            }
            !firebaseEnabled -> {
                (apparatusRepository as PersistentApparatusRepository).ensureDepartmentData(departmentId)
                (inspectionRepository as PersistentInspectionRepository).ensureDepartmentData(departmentId)
                return
            }
        }
        (apparatusRepository as PersistentApparatusRepository).refreshCatalog()
        (inspectionRepository as PersistentInspectionRepository).refreshCatalog()
    }

    fun requestBackgroundSync(departmentId: String) {
        if (!firebaseEnabled || departmentId.isBlank()) return
        applicationScope.launch {
            syncCoordinator.syncDepartment(departmentId)
        }
    }
}
