package com.example.firestationops

import android.app.Application
import com.example.firestationops.sync.SyncScheduler

class FirestationOpsApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = AppGraph(this)
        SyncDependencies.coordinator = appGraph.syncCoordinator
        SyncDependencies.departmentIdProvider = {
            val state = appGraph.authRepository.userState.value
            (state as? com.example.firestationops.domain.model.UserState.Authenticated)?.member?.departmentId
        }
        if (appGraph.firebaseEnabled) {
            SyncScheduler.schedule(this)
        }
    }
}

object SyncDependencies {
    var coordinator: com.example.firestationops.domain.sync.SyncCoordinator? = null
    var departmentIdProvider: () -> String? = { null }
}
