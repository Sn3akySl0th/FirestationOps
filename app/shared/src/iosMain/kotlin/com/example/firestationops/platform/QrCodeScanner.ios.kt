package com.example.firestationops.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberQrCodeScanner(): QrCodeScanner = remember {
    UnsupportedQrCodeScanner("Camera scanning is not available on iOS in this build. Enter the apparatus tag manually.")
}
