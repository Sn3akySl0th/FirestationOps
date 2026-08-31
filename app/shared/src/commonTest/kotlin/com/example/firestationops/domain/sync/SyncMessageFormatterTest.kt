package com.example.firestationops.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncMessageFormatterTest {
    @Test
    fun format_reportsPartialSyncWhenSomeItemsFail() {
        val message = SyncMessageFormatter.format(
            SyncResult(
                uploadedCount = 2,
                downloadedCount = 5,
                failedCount = 1,
                errors = listOf("Attachment att-1: Permission denied")
            )
        )

        assertEquals(
            "Partial sync: downloaded 5, uploaded 2. 1 item(s) failed. Attachment att-1: Permission denied",
            message
        )
    }

    @Test
    fun format_reportsUpToDateWhenNothingChanged() {
        assertEquals(
            "Everything is already up to date.",
            SyncMessageFormatter.format(SyncResult())
        )
    }

    @Test
    fun format_reportsDownloadOnlySuccess() {
        assertEquals(
            "Downloaded 3 record(s) from the cloud.",
            SyncMessageFormatter.format(SyncResult(downloadedCount = 3))
        )
    }
}
