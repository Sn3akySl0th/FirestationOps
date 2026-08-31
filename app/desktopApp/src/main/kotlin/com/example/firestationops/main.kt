package com.example.firestationops

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    val graph = DesktopAppGraph()
    println("DesktopFirebase: firebaseEnabled=${graph.firebaseEnabled}")

    Window(
        onCloseRequest = ::exitApplication,
        title = "FirestationOps",
    ) {
        App(
            authRepository = graph.authRepository,
            apparatusRepository = graph.apparatusRepository,
            inspectionRepository = graph.inspectionRepository,
            deficiencyRepository = graph.deficiencyRepository,
            attachmentRepository = graph.attachmentRepository,
            incidentRepository = graph.incidentRepository,
            departmentRepository = graph.departmentRepository,
            memberRosterRepository = graph.memberRosterRepository,
            catalogAdminRepository = graph.catalogAdminRepository,
            departmentCatalogBootstrap = graph.departmentCatalogBootstrap,
            syncCoordinator = graph.syncCoordinator,
            syncConflictRepository = graph.syncConflictRepository,
            onRequestBackgroundSync = graph::requestBackgroundSync,
            onPrepareDepartment = graph::prepareDepartment
        )
    }
}
