package com.example.firestationops.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private class UnsupportedFileImporter : FileImporter {
    override suspend fun pickTextFile(): TextImportResult =
        TextImportResult.Error("File import is not supported on this platform. Paste CSV text instead.")
}

@Composable
actual fun rememberFileImporter(): FileImporter = remember { UnsupportedFileImporter() }
