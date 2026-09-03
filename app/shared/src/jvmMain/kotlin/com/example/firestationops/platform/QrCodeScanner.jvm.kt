package com.example.firestationops.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberQrCodeScanner(): QrCodeScanner = remember {
    UnsupportedQrCodeScanner("Desktop camera scanning is not available. Enter the apparatus tag manually.")
}
