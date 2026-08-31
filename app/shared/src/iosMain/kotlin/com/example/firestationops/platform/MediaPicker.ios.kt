package com.example.firestationops.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

class IosMediaPicker : MediaPicker {
    @Composable
    override fun registerPicker(onResult: (String?) -> Unit) {}
    override fun launchCamera() {}

    override fun launchGallery() {}
}

@Composable
actual fun rememberMediaPicker(onResult: (String?) -> Unit): MediaPicker = remember { IosMediaPicker() }
