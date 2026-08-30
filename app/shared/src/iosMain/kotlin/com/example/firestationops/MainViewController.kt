package com.example.firestationops

import androidx.compose.ui.window.ComposeUIViewController
import com.example.firestationops.db.DatabaseDriverFactory
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.repository.persistent.PersistentAttachmentRepository
import com.example.firestationops.domain.repository.persistent.PersistentApparatusRepository
import com.example.firestationops.domain.repository.persistent.PersistentAuthRepository
import com.example.firestationops.domain.repository.persistent.PersistentDeficiencyRepository
import com.example.firestationops.domain.repository.persistent.PersistentInspectionRepository
import com.example.firestationops.domain.repository.persistent.PersistentIncidentRepository
import com.example.firestationops.domain.repository.persistent.PersistentDepartmentRepository

fun MainViewController() = ComposeUIViewController {
    val driver = DatabaseDriverFactory().createDriver()
    val database = FirestationOpsDatabase(driver)
    
    val authRepository = PersistentAuthRepository(database)
    val apparatusRepository = PersistentApparatusRepository(database)
    val inspectionRepository = PersistentInspectionRepository(database)
    val deficiencyRepository = PersistentDeficiencyRepository(database)
    val attachmentRepository = PersistentAttachmentRepository(database)
    val incidentRepository = PersistentIncidentRepository(database)
    val departmentRepository = PersistentDepartmentRepository(database)

    App(
        authRepository = authRepository,
        apparatusRepository = apparatusRepository,
        inspectionRepository = inspectionRepository,
        deficiencyRepository = deficiencyRepository,
        attachmentRepository = attachmentRepository,
        incidentRepository = incidentRepository,
        departmentRepository = departmentRepository
    )
}
