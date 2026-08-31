package com.example.firestationops

import kotlinx.browser.window

class JSPlatform : Platform {
    override val name: String = "JavaScript ${window.navigator.userAgent}"
}

actual fun getPlatform(): Platform = JSPlatform()

actual fun currentTimeMillis(): Long = kotlin.js.Date.now().toLong()
actual fun randomUUID(): String = js("globalThis.crypto.randomUUID()") as String
