package com.example.firestationops

class JVMPlatform : Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
actual fun randomUUID(): String = java.util.UUID.randomUUID().toString()
