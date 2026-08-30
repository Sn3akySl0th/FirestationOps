package com.example.firestationops.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class JvmMediaPicker : MediaPicker {
    @Composable
    override fun registerPicker(onResult: (String?) -> Unit) {}
    override fun launch() {}
}

@Composable
actual fun rememberMediaPicker(onResult: (String?) -> Unit): MediaPicker = remember { JvmMediaPicker() }
