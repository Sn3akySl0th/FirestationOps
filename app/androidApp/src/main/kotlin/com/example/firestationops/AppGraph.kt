package com.example.firestationops

import android.content.Context
import com.example.firestationops.data.firebase.FirebaseAuthRepository
import com.example.firestationops.data.firebase.FirebaseAvailability
import com.example.firestationops.data.firebase.FirebaseDepartmentCatalogBootstrap
import com.example.firestationops.data.firebase.FirebaseMemberAccountProvisioner
import com.example.firestationops.data.firebase.FirebaseMemberRosterRepository
import com.example.firestationops.data.firebase.FirebaseSyncCoordinator
import com.example.firestationops.db.DatabaseDriverFactory
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.bootstrap.DepartmentCatalogBootstrap
import com.example.firestationops.domain.bootstrap.DemoDepartmentSeeder
import com.example.firestationops.domain.bootstrap.DepartmentCatalogProfiles
import com.example.firestationops.domain.bootstrap.NoOpDepartmentCatalogBootstrap
import com.example.firestationops.domain.repository.ApparatusRepository
import com.example.firestationops.domain.repository.AttachmentRepository
import com.example.firestationops.domain.repository.AuthRepository
import com.example.firestationops.domain.repository.CatalogRepository
import com.example.firestationops.domain.repository.DeficiencyRepository
import com.example.firestationops.domain.repository.DepartmentRepository
import com.example.firestationops.domain.repository.IncidentRepository
import com.example.firestationops.domain.repository.MemberRosterRepository
import com.example.firestationops.domain.repository.InspectionRepository
import com.example.firestationops.domain.repository.persistent.PersistentApparatusRepository
import com.example.firestationops.domain.repository.persistent.PersistentAttachmentRepository
import com.example.firestationops.domain.repository.persistent.PersistentAuthRepository
import com.example.firestationops.domain.repository.persistent.PersistentCatalogRepository
import com.example.firestationops.domain.repository.persistent.PersistentDeficiencyRepository
import com.example.firestationops.domain.repository.persistent.PersistentDepartmentRepository
import com.example.firestationops.domain.repository.persistent.PersistentIncidentRepository
import com.example.firestationops.domain.repository.persistent.PersistentInspectionRepository
import com.example.firestationops.domain.repository.persistent.PersistentMemberRosterRepository
import com.example.firestationops.domain.sync.NoOpSyncCoordinator
import com.example.firestationops.domain.sync.SyncCoordinator

class AppGraph(context: Context) {
    val database: FirestationOpsDatabase = FirestationOpsDatabase(
        DatabaseDriverFactory(context).createDriver()
    )

    val apparatusRepository: ApparatusRepository = PersistentApparatusRepository(database)
    val inspectionRepository: InspectionRepository = PersistentInspectionRepository(database)
    val deficiencyRepository: DeficiencyRepository = PersistentDeficiencyRepository(database)
    val attachmentRepository: AttachmentRepository = PersistentAttachmentRepository(database)
    val incidentRepository: IncidentRepository = PersistentIncidentRepository(database)
    val departmentRepository: DepartmentRepository = PersistentDepartmentRepository(database)

    private val localAuthRepository: PersistentAuthRepository = PersistentAuthRepository(database)
    val firebaseEnabled: Boolean = FirebaseAvailability.isConfigured(context)

    private val localMemberRosterRepository = PersistentMemberRosterRepository(database)
    private val memberAccountProvisioner = FirebaseMemberAccountProvisioner(context)
    val memberRosterRepository: MemberRosterRepository = if (firebaseEnabled) {
        FirebaseMemberRosterRepository(
            local = localMemberRosterRepository,
            database = database,
            firebaseEnabled = true,
            accountProvisioner = memberAccountProvisioner
        )
    } else {
        localMemberRosterRepository
    }

    val catalogRepository: CatalogRepository = PersistentCatalogRepository(database) {
        (apparatusRepository as PersistentApparatusRepository).refreshCatalog()
        (inspectionRepository as PersistentInspectionRepository).refreshCatalog()
    }

    val departmentCatalogBootstrap: DepartmentCatalogBootstrap = if (firebaseEnabled) {
        FirebaseDepartmentCatalogBootstrap(
            firebaseEnabled = true,
            database = database
        )
    } else {
        NoOpDepartmentCatalogBootstrap()
    }

    val authRepository: AuthRepository = if (firebaseEnabled) {
        FirebaseAuthRepository(
            database = database,
            localAuth = localAuthRepository,
            firebaseEnabled = true
        )
    } else {
        localAuthRepository
    }

    val syncCoordinator: SyncCoordinator = if (firebaseEnabled) {
        FirebaseSyncCoordinator(
            context = context,
            firebaseEnabled = true,
            catalogRepository = catalogRepository,
            attachmentRepository = attachmentRepository,
            inspectionRepository = inspectionRepository,
            deficiencyRepository = deficiencyRepository,
            incidentRepository = incidentRepository
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
}
