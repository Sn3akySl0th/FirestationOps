package com.example.firestationops.data.firebase

import com.google.firebase.FirebasePlatform
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists Firebase Java SDK auth state for desktop restarts.
 *
 * The default bootstrap used an in-memory map, so [com.google.firebase.auth.FirebaseAuth]
 * always started signed out after closing the app.
 */
internal class JvmFirebasePlatformStorage(
    storageFile: File = defaultStorageFile()
) : FirebasePlatform() {
    private val json = Json { ignoreUnknownKeys = true }
    private val storageFile = storageFile
    private var storage: MutableMap<String, String> = loadStorage()

    override fun store(key: String, value: String) {
        storage[key] = value
        persistStorage()
    }

    override fun retrieve(key: String): String? = storage[key]

    override fun clear(key: String) {
        if (storage.remove(key) != null) {
            persistStorage()
        }
    }

    override fun log(msg: String) {
        println(msg)
    }

    private fun loadStorage(): MutableMap<String, String> {
        if (!storageFile.exists()) return mutableMapOf()
        return runCatching {
            json.decodeFromString<Map<String, String>>(storageFile.readText()).toMutableMap()
        }.getOrElse { mutableMapOf() }
    }

    private fun persistStorage() {
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(json.encodeToString(storage))
    }

    companion object {
        fun defaultStorageFile(): File =
            File(System.getProperty("user.home"), ".firestationops/firebase-platform.json")
    }
}
