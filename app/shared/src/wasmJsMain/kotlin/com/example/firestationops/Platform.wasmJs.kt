package com.example.firestationops

class WasmJsPlatform : Platform {
    override val name: String = "WebAssembly"
}

actual fun getPlatform(): Platform = WasmJsPlatform()

actual fun currentTimeMillis(): Long = 0L // TODO: Implement for wasmJs
actual fun randomUUID(): String = "" // TODO: Implement for wasmJs
