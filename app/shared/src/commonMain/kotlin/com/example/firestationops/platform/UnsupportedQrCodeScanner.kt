package com.example.firestationops.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class UnsupportedQrCodeScanner(
    private val placeholderMessage: String = "Camera scanning is not available on this device. Enter the tag manually."
) : QrCodeScanner {
    override val isCameraSupported: Boolean = false

    @Composable
    override fun RegisterPermissionHandler(
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit
    ) {
    }

    override fun requestCameraPermission() {
    }

    override fun hasCameraPermission(): Boolean = false

    @Composable
    override fun CameraScannerPreview(
        onBarcodeDetected: (String) -> Unit,
        isTorchEnabled: Boolean,
        modifier: Modifier
    ) {
        Box(
            modifier = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = placeholderMessage,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
