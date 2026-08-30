package com.example.firestationops

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun currentTimeMillis(): Long
expect fun randomUUID(): String
