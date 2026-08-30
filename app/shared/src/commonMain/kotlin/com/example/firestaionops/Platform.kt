package com.example.firestaionops

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform