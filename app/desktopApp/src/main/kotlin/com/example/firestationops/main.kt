package com.example.firestationops

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.firestationops.db.DatabaseDriverFactory
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.repository.persistent.PersistentApparatusRepository
import com.example.firestationops.domain.repository.persistent.PersistentAuthRepository
import com.example.firestationops.domain.repository.persistent.PersistentDeficiencyRepository
import com.example.firestationops.domain.repository.persistent.PersistentInspectionRepository
import com.example.firestationops.domain.repository.persistent.PersistentAttachmentRepository
import com.example.firestationops.domain.repository.persistent.PersistentIncidentRepository
import com.example.firestationops.domain.repository.persistent.PersistentDepartmentRepository
import com.example.firestationops.domain.bootstrap.NoOpDepartmentCatalogBootstrap
import com.example.firestationops.domain.sync.NoOpSyncCoordinator

fun main() = application {
    val driver = DatabaseDriverFactory().createDriver()
    val database = FirestationOpsDatabase(driver)
    
    val authRepository = PersistentAuthRepository(database)
    val apparatusRepository = PersistentApparatusRepository(database)
    val inspectionRepository = PersistentInspectionRepository(database)
    val deficiencyRepository = PersistentDeficiencyRepository(database)
    val attachmentRepository = PersistentAttachmentRepository(database)
    val incidentRepository = PersistentIncidentRepository(database)
    val departmentRepository = PersistentDepartmentRepository(database)

    Window(
        onCloseRequest = ::exitApplication,
        title = "FirestationOps",
    ) {
        App(
            authRepository = authRepository,
            apparatusRepository = apparatusRepository,
            inspectionRepository = inspectionRepository,
            deficiencyRepository = deficiencyRepository,
            attachmentRepository = attachmentRepository,
            incidentRepository = incidentRepository,
            departmentRepository = departmentRepository,
            departmentCatalogBootstrap = NoOpDepartmentCatalogBootstrap(),
            syncCoordinator = NoOpSyncCoordinator(),
            onPrepareDepartment = { departmentId ->
                (apparatusRepository as PersistentApparatusRepository).ensureDepartmentData(departmentId)
                (inspectionRepository as PersistentInspectionRepository).ensureDepartmentData(departmentId)
            }
        )
    }
}
