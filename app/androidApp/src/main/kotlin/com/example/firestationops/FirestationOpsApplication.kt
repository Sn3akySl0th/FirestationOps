package com.example.firestationops

import android.app.Application
import android.content.pm.ApplicationInfo
import com.example.firestationops.data.firebase.AndroidFirebaseBootstrap
import com.example.firestationops.sync.SyncScheduler

class FirestationOpsApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        val isDebugBuild = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (FirebaseAvailabilityHelper.isConfigured(this)) {
            AndroidFirebaseBootstrap.initialize(this, isDebugBuild)
        }
        appGraph = AppGraph(this, isDebugBuild)
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
