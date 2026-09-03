package com.example.firestationops.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class AndroidQrCodeScanner(private val context: Context) : QrCodeScanner {
    override val isCameraSupported: Boolean = true

    private var permissionLauncher: ManagedActivityResultLauncher<String, Boolean>? = null
    private var onGrantedCallback: (() -> Unit)? = null
    private var onDeniedCallback: (() -> Unit)? = null

    @Composable
    override fun RegisterPermissionHandler(
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit
    ) {
        val granted by rememberUpdatedState(onPermissionGranted)
        val denied by rememberUpdatedState(onPermissionDenied)
        onGrantedCallback = granted
        onDeniedCallback = denied

        permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                onGrantedCallback?.invoke()
            } else {
                onDeniedCallback?.invoke()
            }
        }
    }

    override fun requestCameraPermission() {
        permissionLauncher?.launch(Manifest.permission.CAMERA)
    }

    override fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    @OptIn(ExperimentalGetImage::class)
    @Composable
    override fun CameraScannerPreview(
        onBarcodeDetected: (String) -> Unit,
        isTorchEnabled: Boolean,
        modifier: Modifier
    ) {
        val currentContext = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val onDetected by rememberUpdatedState(onBarcodeDetected)
        var cameraInstance by remember { mutableStateOf<Camera?>(null) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

        LaunchedEffect(isTorchEnabled, cameraInstance) {
            val camera = cameraInstance ?: return@LaunchedEffect
            if (camera.cameraInfo.hasFlashUnit()) {
                runCatching { camera.cameraControl.enableTorch(isTorchEnabled) }
            }
        }

        Box(modifier = modifier) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also { previewUseCase ->
                                previewUseCase.surfaceProvider = previewView.surfaceProvider
                            }
                            val scanner = BarcodeScanning.getClient(
                                BarcodeScannerOptions.Builder()
                                    .setBarcodeFormats(
                                        Barcode.FORMAT_QR_CODE,
                                        Barcode.FORMAT_CODE_128,
                                        Barcode.FORMAT_CODE_39,
                                        Barcode.FORMAT_EAN_13,
                                        Barcode.FORMAT_EAN_8,
                                        Barcode.FORMAT_UPC_A,
                                        Barcode.FORMAT_UPC_E,
                                        Barcode.FORMAT_DATA_MATRIX,
                                        Barcode.FORMAT_PDF417
                                    )
                                    .build()
                            )
                            val lastScannedCode = AtomicReference("")
                            val lastScannedAt = AtomicLong(0L)
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage == null) {
                                    imageProxy.close()
                                    return@setAnalyzer
                                }
                                val inputImage = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                scanner.process(inputImage)
                                    .addOnSuccessListener { barcodes ->
                                        val rawValue = barcodes.firstNotNullOfOrNull { it.rawValue }
                                            ?.trim()
                                            .orEmpty()
                                        if (rawValue.isBlank()) return@addOnSuccessListener
                                        val now = System.currentTimeMillis()
                                        val previous = lastScannedCode.get()
                                        if (rawValue != previous || now - lastScannedAt.get() > 2_000L) {
                                            lastScannedCode.set(rawValue)
                                            lastScannedAt.set(now)
                                            onDetected(rawValue)
                                        }
                                    }
                                    .addOnFailureListener { error ->
                                        Log.w(TAG, "Barcode scan failure", error)
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            }
                            cameraProvider.unbindAll()
                            cameraInstance = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (error: Exception) {
                            Log.e(TAG, "Camera initialization failed", error)
                            errorMessage = "Camera error. Enter the apparatus tag manually."
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )

            errorMessage?.let { error ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        DisposableEffect(currentContext, cameraExecutor) {
            onDispose {
                runCatching {
                    ProcessCameraProvider.getInstance(currentContext).get().unbindAll()
                }
                cameraExecutor.shutdown()
            }
        }
    }

    private companion object {
        const val TAG = "ApparatusScanner"
    }
}

@Composable
actual fun rememberQrCodeScanner(): QrCodeScanner {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        AndroidQrCodeScanner(context.applicationContext)
    }
}
