package com.example.firestationops.domain.sync

object SyncMessageFormatter {
    fun format(result: SyncResult): String = when {
        result.isSuccess && result.uploadedCount > 0 && result.downloadedCount > 0 ->
            "Downloaded ${result.downloadedCount} and uploaded ${result.uploadedCount} record(s)."
        result.isSuccess && result.uploadedCount > 0 ->
            "Synced ${result.uploadedCount} record(s) to the cloud."
        result.isSuccess && result.downloadedCount > 0 ->
            "Downloaded ${result.downloadedCount} record(s) from the cloud."
        result.isSuccess ->
            "Everything is already up to date."
        result.hasPartialSuccess ->
            buildString {
                append("Partial sync")
                if (result.downloadedCount > 0) {
                    append(": downloaded ${result.downloadedCount}")
                }
                if (result.uploadedCount > 0) {
                    append(if (result.downloadedCount > 0) ", uploaded ${result.uploadedCount}" else ": uploaded ${result.uploadedCount}")
                }
                append(". ${result.failedCount} item(s) failed.")
                result.errors.firstOrNull()?.let { append(" $it") }
            }
        else ->
            result.errors.firstOrNull() ?: "Sync failed."
    }
}
