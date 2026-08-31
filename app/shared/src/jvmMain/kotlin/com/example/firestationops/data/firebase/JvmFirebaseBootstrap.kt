package com.example.firestationops.data.firebase

import android.app.Application
import com.google.firebase.FirebasePlatform
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions as GitLiveFirebaseOptions
import dev.gitlive.firebase.initialize

object JvmFirebaseBootstrap {
    private var configured = false

    fun isConfigured(): Boolean = configured

    fun initializeIfConfigured(): Boolean {
        if (configured) return true

        val config = DesktopFirebaseConfig.load() ?: run {
            println("DesktopFirebase: no config file found; using local auth only")
            return false
        }

        return runCatching {
            initializeFirebase(config)
            true
        }.onFailure { error ->
            println("DesktopFirebase: bootstrap failed: ${error.message}")
            error.printStackTrace()
        }.getOrDefault(false)
    }

    private fun initializeFirebase(config: DesktopFirebaseConfig) {
        FirebasePlatform.initializeFirebasePlatform(JvmFirebasePlatformStorage())

        Firebase.initialize(
            Application(),
            GitLiveFirebaseOptions(
                applicationId = config.applicationId,
                apiKey = config.apiKey,
                projectId = config.projectId,
                storageBucket = config.storageBucket
            )
        )

        val firestore = FirebaseFirestore.getInstance()
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder(firestore.firestoreSettings)
            .setPersistenceEnabled(false)
            .build()

        configured = true
        println(
            "DesktopFirebase: initialized for project ${config.projectId} " +
                "(cloudAuth=${com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null})"
        )
    }
}
