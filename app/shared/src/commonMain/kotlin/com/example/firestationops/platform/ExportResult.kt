package com.example.firestationops.platform

sealed interface ExportResult {
    data object Success : ExportResult
    data object Cancelled : ExportResult
    data class Error(val message: String) : ExportResult
}
