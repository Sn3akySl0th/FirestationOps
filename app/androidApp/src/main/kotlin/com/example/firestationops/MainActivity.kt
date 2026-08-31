package com.example.firestationops

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.example.firestationops.domain.repository.mock.MockAuthRepository
import com.example.firestationops.domain.repository.mock.MockApparatusRepository
import com.example.firestationops.domain.repository.mock.MockDeficiencyRepository
import com.example.firestationops.domain.repository.mock.MockInspectionRepository
import com.example.firestationops.domain.repository.mock.MockAttachmentRepository
import com.example.firestationops.domain.repository.mock.MockIncidentRepository
import com.example.firestationops.domain.repository.mock.MockDepartmentRepository
import com.example.firestationops.domain.sync.NoOpSyncCoordinator
import com.example.firestationops.sync.SyncScheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val graph = (application as FirestationOpsApplication).appGraph

        setContent {
            App(
                authRepository = graph.authRepository,
                apparatusRepository = graph.apparatusRepository,
                inspectionRepository = graph.inspectionRepository,
                deficiencyRepository = graph.deficiencyRepository,
                attachmentRepository = graph.attachmentRepository,
                incidentRepository = graph.incidentRepository,
                departmentRepository = graph.departmentRepository,
                departmentCatalogBootstrap = graph.departmentCatalogBootstrap,
                syncCoordinator = graph.syncCoordinator,
                onRequestBackgroundSync = {
                    if (graph.firebaseEnabled) {
                        SyncScheduler.runNow(this@MainActivity)
                    }
                },
                onPrepareDepartment = graph::prepareDepartment
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
        departmentRepository = departmentRepository,
        syncCoordinator = remember { NoOpSyncCoordinator() }
    )
}
