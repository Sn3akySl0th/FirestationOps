package com.example.firestationops.data.firebase

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JvmFirebasePlatformStorageTest {
    @Test
    fun storeAndRetrieve_persistsAcrossInstances() {
        val file = File.createTempFile("firebase-platform", ".json")
        file.deleteOnExit()

        val storage1 = JvmFirebasePlatformStorage(file)
        storage1.store("auth-token", "persisted-value")

        val storage2 = JvmFirebasePlatformStorage(file)
        assertEquals("persisted-value", storage2.retrieve("auth-token"))
    }

    @Test
    fun clear_removesPersistedValue() {
        val file = File.createTempFile("firebase-platform", ".json")
        file.deleteOnExit()

        val storage1 = JvmFirebasePlatformStorage(file)
        storage1.store("auth-token", "persisted-value")

        val storage2 = JvmFirebasePlatformStorage(file)
        storage2.clear("auth-token")

        val storage3 = JvmFirebasePlatformStorage(file)
        assertNull(storage3.retrieve("auth-token"))
    }
}
