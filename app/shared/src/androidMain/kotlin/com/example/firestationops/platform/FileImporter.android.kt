package com.example.firestationops.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidFileImporter : FileImporter {
    private var launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null
    private var pendingContinuation: CancellableContinuation<TextImportResult>? = null
    private var contentResolver: android.content.ContentResolver? = null

    @Composable
    fun register() {
        val context = LocalContext.current
        contentResolver = context.contentResolver
        launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            val continuation = pendingContinuation
            pendingContinuation = null
            if (continuation == null) return@rememberLauncherForActivityResult

            if (uri == null) {
                continuation.resume(TextImportResult.Cancelled)
                return@rememberLauncherForActivityResult
            }

            try {
                val resolver = contentResolver
                    ?: throw IllegalStateException("Content resolver is not ready")
                val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                }
                val text = resolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: throw IllegalStateException("Unable to open selected file")
                continuation.resume(TextImportResult.Success(content = text, fileName = name))
            } catch (e: Exception) {
                continuation.resume(TextImportResult.Error(e.message ?: "Failed to read file"))
            }
        }
    }

    override suspend fun pickTextFile(): TextImportResult =
        suspendCancellableCoroutine { continuation ->
            val launcher = launcher
            if (launcher == null) {
                continuation.resume(TextImportResult.Error("File importer is not ready"))
                return@suspendCancellableCoroutine
            }
            pendingContinuation = continuation
            continuation.invokeOnCancellation {
                pendingContinuation = null
            }
            launcher.launch(arrayOf("text/*", "text/csv", "application/csv", "*/*"))
        }
}

@Composable
actual fun rememberFileImporter(): FileImporter {
    val importer = remember { AndroidFileImporter() }
    importer.register()
    return importer
}
