package com.example.firestationops.domain.sync

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncMessageFormatterTest {
    @Test
    fun format_reportsPartialSyncWhenSomeItemsFail() {
        val message = SyncMessageFormatter.format(
            SyncResult(
                uploadedItems = listOf(
                    SyncActivityItem(
                        direction = SyncActivityDirection.UPLOAD,
                        recordType = SyncActivityRecordType.INSPECTION,
                        recordId = "insp-1",
                        title = "E5 inspection",
                        detail = null,
                        action = SyncActivityAction.UPDATED
                    ),
                    SyncActivityItem(
                        direction = SyncActivityDirection.UPLOAD,
                        recordType = SyncActivityRecordType.INSPECTION,
                        recordId = "insp-2",
                        title = "B5 inspection",
                        detail = null,
                        action = SyncActivityAction.UPDATED
                    )
                ),
                downloadedItems = List(5) { index ->
                    SyncActivityItem(
                        direction = SyncActivityDirection.DOWNLOAD,
                        recordType = SyncActivityRecordType.STATION,
                        recordId = "st-$index",
                        title = "Station $index",
                        detail = null,
                        action = SyncActivityAction.NEW
                    )
                },
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
            "3 new from cloud.",
            SyncMessageFormatter.format(
                SyncResult(
                    downloadedItems = List(3) {
                        SyncActivityItem(
                            direction = SyncActivityDirection.DOWNLOAD,
                            recordType = SyncActivityRecordType.APPARATUS,
                            recordId = "ap-$it",
                            title = "Engine $it",
                            detail = null,
                            action = SyncActivityAction.NEW
                        )
                    }
                )
            )
        )
    }

    @Test
    fun format_reportsUpdatedOnlySuccess() {
        assertEquals(
            "5 updated from cloud.",
            SyncMessageFormatter.format(
                SyncResult(
                    downloadedItems = List(5) {
                        SyncActivityItem(
                            direction = SyncActivityDirection.DOWNLOAD,
                            recordType = SyncActivityRecordType.STATION,
                            recordId = "st-$it",
                            title = "Station $it",
                            detail = null,
                            action = SyncActivityAction.UPDATED
                        )
                    }
                )
            )
        )
    }
}
