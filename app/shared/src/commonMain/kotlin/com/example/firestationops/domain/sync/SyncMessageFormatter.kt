package com.example.firestationops.domain.sync

object SyncMessageFormatter {
    fun format(result: SyncResult): String = when {
        result.isSuccess && result.uploadedCount > 0 && result.downloadedCount > 0 ->
            "Downloaded ${result.downloadedCount} and uploaded ${result.uploadedCount} record(s)."
        result.isSuccess && result.uploadedCount > 0 ->
            "Synced ${result.uploadedCount} record(s) to the cloud."
        result.isSuccess && result.downloadedCount > 0 && result.uploadedCount == 0 ->
            downloadedSummary(result).replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + "."
        result.isSuccess && result.downloadedCount > 0 ->
            "Downloaded ${result.downloadedCount} record(s) from the cloud."
        result.isSuccess ->
            "Everything is already up to date."
        result.conflictCount > 0 && result.failedCount == 0 ->
            "${result.conflictCount} sync conflict${if (result.conflictCount == 1) "" else "s"} need officer review."
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

    fun downloadedSummary(result: SyncResult): String = when {
        result.downloadedItems.isEmpty() -> "No new cloud records downloaded."
        else -> buildList {
            val newCount = result.downloadedItems.count { it.action == SyncActivityAction.NEW }
            val updatedCount = result.downloadedItems.count { it.action == SyncActivityAction.UPDATED }
            if (newCount > 0) add("$newCount new")
            if (updatedCount > 0) add("$updatedCount updated")
        }.joinToString(", ") + " from cloud"
    }

    fun uploadedSummary(result: SyncResult): String = when {
        result.uploadedItems.isEmpty() -> "No local records uploaded."
        else -> "${result.uploadedItems.size} uploaded to cloud"
    }
}
