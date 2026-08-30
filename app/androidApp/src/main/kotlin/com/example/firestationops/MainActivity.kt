package com.example.firestationops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.example.firestationops.db.DatabaseDriverFactory
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.repository.mock.MockAuthRepository
import com.example.firestationops.domain.repository.mock.MockApparatusRepository
import com.example.firestationops.domain.repository.mock.MockDeficiencyRepository
import com.example.firestationops.domain.repository.mock.MockInspectionRepository
import com.example.firestationops.domain.repository.mock.MockAttachmentRepository
import com.example.firestationops.domain.repository.mock.MockIncidentRepository
import com.example.firestationops.domain.repository.mock.MockDepartmentRepository
import com.example.firestationops.domain.repository.persistent.PersistentApparatusRepository
import com.example.firestationops.domain.repository.persistent.PersistentAuthRepository
import com.example.firestationops.domain.repository.persistent.PersistentDeficiencyRepository
import com.example.firestationops.domain.repository.persistent.PersistentInspectionRepository
import com.example.firestationops.domain.repository.persistent.PersistentAttachmentRepository
import com.example.firestationops.domain.repository.persistent.PersistentIncidentRepository
import com.example.firestationops.domain.repository.persistent.PersistentDepartmentRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val driver = DatabaseDriverFactory(this).createDriver()
        val database = FirestationOpsDatabase(driver)
        
        val authRepository = PersistentAuthRepository(database)
        val apparatusRepository = PersistentApparatusRepository(database)
        val inspectionRepository = PersistentInspectionRepository(database)
        val deficiencyRepository = PersistentDeficiencyRepository(database)
        val attachmentRepository = PersistentAttachmentRepository(database)
        val incidentRepository = PersistentIncidentRepository(database)
        val departmentRepository = PersistentDepartmentRepository(database)

        setContent {
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
}

@Preview
@Composable
fun AppAndroidPreview() {
    val authRepository = remember { MockAuthRepository() }
    val apparatusRepository = remember { MockApparatusRepository() }
    val inspectionRepository = remember { MockInspectionRepository() }
    val deficiencyRepository = remember { MockDeficiencyRepository() }
    val attachmentRepository = remember { MockAttachmentRepository() }
    val incidentRepository = remember { MockIncidentRepository() }
    val departmentRepository = remember { MockDepartmentRepository() }
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
