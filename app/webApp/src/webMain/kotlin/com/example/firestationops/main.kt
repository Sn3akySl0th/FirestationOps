package com.example.firestationops

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.example.firestationops.domain.repository.mock.MockApparatusRepository
import com.example.firestationops.domain.repository.mock.MockAttachmentRepository
import com.example.firestationops.domain.repository.mock.MockAuthRepository
import com.example.firestationops.domain.repository.mock.MockDeficiencyRepository
import com.example.firestationops.domain.repository.mock.MockInspectionRepository
import com.example.firestationops.domain.repository.mock.MockIncidentRepository
import com.example.firestationops.domain.repository.mock.MockDepartmentRepository

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val authRepository = MockAuthRepository()
    val apparatusRepository = MockApparatusRepository()
    val inspectionRepository = MockInspectionRepository()
    val deficiencyRepository = MockDeficiencyRepository()
    val attachmentRepository = MockAttachmentRepository()
    val incidentRepository = MockIncidentRepository()
    val departmentRepository = MockDepartmentRepository()

    ComposeViewport {
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
}
