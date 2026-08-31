package com.example.firestationops.data.firebase

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DesktopFirebaseConfig(
    val projectId: String,
    val apiKey: String,
    val applicationId: String,
    val storageBucket: String
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(): DesktopFirebaseConfig? {
            val candidates = buildList {
                System.getenv("FIRESTATIONOPS_FIREBASE_CONFIG")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { add(File(it)) }
                add(File(System.getProperty("user.home"), ".firestationops/firebase.json"))
                add(File("firebase-desktop.json"))
            }

            candidates.forEach { file ->
                println("DesktopFirebase: checking ${file.absolutePath} (exists=${file.exists()})")
            }

            return candidates.firstNotNullOfOrNull { file ->
                if (!file.exists()) return@firstNotNullOfOrNull null
                runCatching {
                    json.decodeFromString<DesktopFirebaseConfig>(file.readText().removePrefix("\uFEFF"))
                }.onFailure { error ->
                    println("DesktopFirebase: failed to parse ${file.absolutePath}: ${error.message}")
                }.getOrNull()?.also { config ->
                    println("DesktopFirebase: loaded config for project ${config.projectId}")
                }
            }
        }
    }
}
