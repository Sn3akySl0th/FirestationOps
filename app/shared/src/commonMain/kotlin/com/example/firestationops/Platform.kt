package com.example.firestationops

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform