package com.example.firestationops.platform

import androidx.compose.runtime.Composable

interface FileExporter {
    suspend fun saveTextFile(suggestedFileName: String, content: String): ExportResult
    suspend fun saveBinaryFile(suggestedFileName: String, content: ByteArray): ExportResult
}

@Composable
expect fun rememberFileExporter(): FileExporter
