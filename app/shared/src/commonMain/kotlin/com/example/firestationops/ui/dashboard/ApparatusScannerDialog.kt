package com.example.firestationops.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.firestationops.platform.rememberQrCodeScanner

@Composable
fun ApparatusScannerDialog(
    onDismiss: () -> Unit,
    onTagSubmitted: (String) -> Unit
) {
    val scanner = rememberQrCodeScanner()
    var manualTag by remember { mutableStateOf("") }
    var showCamera by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    scanner.RegisterPermissionHandler(
        onPermissionGranted = {
            permissionDenied = false
            showCamera = true
        },
        onPermissionDenied = {
            permissionDenied = true
            showCamera = false
        }
    )

    LaunchedEffect(Unit) {
        if (scanner.isCameraSupported && scanner.hasCameraPermission()) {
            showCamera = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan apparatus") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Scan a barcode or QR code on the apparatus, or type the radio name, VIN, plate, or assigned barcode.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (scanner.isCameraSupported) {
                    if (showCamera) {
                        scanner.CameraScannerPreview(
                            onBarcodeDetected = onTagSubmitted,
                            isTorchEnabled = torchEnabled,
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Flashlight")
                            Switch(
                                checked = torchEnabled,
                                onCheckedChange = { torchEnabled = it }
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                if (scanner.hasCameraPermission()) {
                                    showCamera = true
                                } else {
                                    scanner.requestCameraPermission()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Scan with camera")
                        }
                        if (permissionDenied) {
                            Text(
                                "Camera permission was denied. Enter the tag manually.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = manualTag,
                    onValueChange = { manualTag = it },
                    label = { Text("Apparatus tag") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onTagSubmitted(manualTag) },
                enabled = manualTag.isNotBlank()
            ) {
                Text("Look up")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
