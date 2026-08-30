package com.example.firestationops.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidFileExporter : FileExporter {
    private var launcher: androidx.activity.result.ActivityResultLauncher<String>? = null
    private var pendingBytes: ByteArray? = null
    private var pendingContinuation: CancellableContinuation<ExportResult>? = null

    @Composable
    fun register() {
        val context = LocalContext.current
        launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("*/*")
        ) { uri ->
            val bytes = pendingBytes
            val continuation = pendingContinuation
            pendingBytes = null
            pendingContinuation = null

            if (continuation == null) return@rememberLauncherForActivityResult

            if (uri == null || bytes == null) {
                continuation.resume(ExportResult.Cancelled)
                return@rememberLauncherForActivityResult
            }

            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(bytes)
                } ?: throw IllegalStateException("Unable to open output stream")
                continuation.resume(ExportResult.Success)
            } catch (e: Exception) {
                continuation.resume(ExportResult.Error(e.message ?: "Failed to save file"))
            }
        }
    }

    override suspend fun saveTextFile(suggestedFileName: String, content: String): ExportResult =
        saveBinaryFile(suggestedFileName, content.toByteArray(Charsets.UTF_8))

    override suspend fun saveBinaryFile(suggestedFileName: String, content: ByteArray): ExportResult =
        suspendCancellableCoroutine { continuation ->
            val launcher = launcher
            if (launcher == null) {
                continuation.resume(ExportResult.Error("File exporter is not ready"))
                return@suspendCancellableCoroutine
            }
            pendingBytes = content
            pendingContinuation = continuation
            continuation.invokeOnCancellation {
                pendingBytes = null
                pendingContinuation = null
            }
            launcher.launch(suggestedFileName)
        }
}

@Composable
actual fun rememberFileExporter(): FileExporter {
    val exporter = remember { AndroidFileExporter() }
    exporter.register()
    return exporter
}
