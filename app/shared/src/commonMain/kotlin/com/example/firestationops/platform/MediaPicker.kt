package com.example.firestationops.platform

import androidx.compose.runtime.Composable

interface MediaPicker {
    @Composable
    fun registerPicker(onResult: (String?) -> Unit)

    fun launchCamera()

    fun launchGallery()
}

@Composable
expect fun rememberMediaPicker(onResult: (String?) -> Unit): MediaPicker
