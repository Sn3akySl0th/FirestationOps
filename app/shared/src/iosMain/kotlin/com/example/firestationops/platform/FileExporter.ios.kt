package com.example.firestationops.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private class UnsupportedFileExporter : FileExporter {
    override suspend fun saveTextFile(suggestedFileName: String, content: String): ExportResult =
        ExportResult.Error("File export is not supported on this platform")

    override suspend fun saveBinaryFile(suggestedFileName: String, content: ByteArray): ExportResult =
        ExportResult.Error("File export is not supported on this platform")
}

@Composable
actual fun rememberFileExporter(): FileExporter = remember { UnsupportedFileExporter() }
