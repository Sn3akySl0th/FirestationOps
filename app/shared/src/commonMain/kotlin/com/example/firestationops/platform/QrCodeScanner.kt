package com.example.firestationops.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface QrCodeScanner {
    val isCameraSupported: Boolean

    @Composable
    fun RegisterPermissionHandler(
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit
    )

    fun requestCameraPermission()

    fun hasCameraPermission(): Boolean

    @Composable
    fun CameraScannerPreview(
        onBarcodeDetected: (String) -> Unit,
        isTorchEnabled: Boolean,
        modifier: Modifier = Modifier
    )
}

@Composable
expect fun rememberQrCodeScanner(): QrCodeScanner
