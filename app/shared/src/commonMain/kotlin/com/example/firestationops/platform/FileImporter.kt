package com.example.firestationops.platform

import androidx.compose.runtime.Composable

sealed class TextImportResult {
    data class Success(val content: String, val fileName: String? = null) : TextImportResult()
    data object Cancelled : TextImportResult()
    data class Error(val message: String) : TextImportResult()
}

interface FileImporter {
    suspend fun pickTextFile(): TextImportResult
}

@Composable
expect fun rememberFileImporter(): FileImporter
