package com.example.firestationops.domain.sync

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.DeficiencyStatus
import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.InspectionTemplate
import com.example.firestationops.domain.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingSyncQueueBuilderTest {
    @Test
    fun build_listsPendingInspectionWithApparatusAndTemplateLabels() {
        val queue = PendingSyncQueueBuilder.build(
            PendingSyncQueueBuilder.Input(
                inspections = listOf(
                    Inspection(
                        id = "insp-1",
                        templateId = "tmpl-engine",
                        apparatusId = "ap-engine-5",
                        departmentId = "5",
                        startedAt = 0,
                        startedByUserId = "user-1",
                        syncStatus = SyncStatus.PENDING_SYNC
                    )
                ),
                deficiencies = emptyList(),
                attachments = emptyList(),
                incidents = emptyList(),
                apparatusById = mapOf(
                    "ap-engine-5" to Apparatus(
                        id = "ap-engine-5",
                        departmentId = "5",
                        stationId = "st-5",
                        name = "Engine 5",
                        type = "Engine",
                        radioName = "E5"
                    )
                ),
                templatesById = mapOf(
                    "tmpl-engine" to InspectionTemplate(
                        id = "tmpl-engine",
                        departmentId = "5",
                        name = "Daily Engine Inspection",
                        apparatusType = "Engine",
                        frequencyHours = 24,
                        items = emptyList()
                    )
                )
            )
        )

        assertEquals(1, queue.pendingItems.size)
        val item = queue.pendingItems.single()
        assertEquals(PendingSyncRecordType.INSPECTION, item.recordType)
        assertEquals("E5 · Daily Engine Inspection", item.title)
        assertEquals("Draft", item.detail)
        assertEquals(SyncStatus.PENDING_SYNC, item.syncStatus)
    }

    @Test
    fun build_countsSyncedRecordsByType() {
        val queue = PendingSyncQueueBuilder.build(
            PendingSyncQueueBuilder.Input(
                inspections = listOf(
                    syncedInspection("insp-synced"),
                    pendingInspection("insp-pending")
                ),
                deficiencies = listOf(
                    Deficiency(
                        id = "def-1",
                        apparatusId = "ap-engine-5",
                        departmentId = "5",
                        title = "Pump leak",
                        description = "Leak",
                        severity = DeficiencySeverity.REPAIR_NEEDED,
                        status = DeficiencyStatus.OPEN,
                        createdAt = 0,
                        createdByUserId = "user-1",
                        syncStatus = SyncStatus.SYNCED
                    )
                ),
                attachments = emptyList(),
                incidents = emptyList(),
                apparatusById = emptyMap(),
                templatesById = emptyMap()
            )
        )

        assertEquals(1, queue.pendingItems.size)
        assertEquals("insp-pending", queue.pendingItems.single().recordId)
        assertEquals(1, queue.syncedCounts.inspections)
        assertEquals(1, queue.syncedCounts.deficiencies)
        assertTrue(queue.syncedCounts.summaryLabel().contains("1 inspection"))
        assertTrue(queue.syncedCounts.summaryLabel().contains("1 deficiency"))
    }

    @Test
    fun build_failedAttachmentIncludesErrorAndRetryFlag() {
        val queue = PendingSyncQueueBuilder.build(
            PendingSyncQueueBuilder.Input(
                inspections = listOf(
                    Inspection(
                        id = "insp-1",
                        templateId = "tmpl-engine",
                        apparatusId = "ap-engine-5",
                        departmentId = "5",
                        startedAt = 0,
                        startedByUserId = "user-1",
                        syncStatus = SyncStatus.SYNCED,
                        responses = listOf(
                            com.example.firestationops.domain.model.InspectionResponse(
                                itemId = "item-1",
                                status = com.example.firestationops.domain.model.InspectionStatus.FAIL,
                                attachmentIds = listOf("att-1")
                            )
                        )
                    )
                ),
                deficiencies = emptyList(),
                attachments = listOf(
                    com.example.firestationops.domain.model.Attachment(
                        id = "att-1",
                        departmentId = "5",
                        localUri = "/data/sync_attachments/att-1.jpg",
                        syncStatus = SyncStatus.SYNC_FAILED,
                        createdAt = 0,
                        createdByUserId = "user-1",
                        lastError = "Network timeout",
                        failedAt = 1
                    )
                ),
                incidents = emptyList(),
                apparatusById = mapOf(
                    "ap-engine-5" to Apparatus(
                        id = "ap-engine-5",
                        departmentId = "5",
                        stationId = "st-5",
                        name = "Engine 5",
                        type = "Engine",
                        radioName = "E5"
                    )
                ),
                templatesById = mapOf(
                    "tmpl-engine" to InspectionTemplate(
                        id = "tmpl-engine",
                        departmentId = "5",
                        name = "Daily Engine Inspection",
                        apparatusType = "Engine",
                        frequencyHours = 24,
                        items = listOf(
                            com.example.firestationops.domain.model.InspectionTemplateItem(
                                id = "item-1",
                                text = "Pump pressure gauge"
                            )
                        )
                    )
                )
            )
        )

        val item = queue.pendingItems.single { it.recordType == PendingSyncRecordType.ATTACHMENT }
        assertEquals("E5 · Daily Engine Inspection", item.title)
        assertTrue(item.detail?.contains("Pump pressure gauge") == true)
        assertTrue(item.detail?.contains("Network timeout") == true)
        assertEquals(true, item.canRetry)
        assertEquals(1, queue.failedAttachmentCount)
    }

    @Test
    fun statusLabel_describesPendingAndFailedStates() {
        assertEquals("Waiting to upload", PendingSyncQueueBuilder.statusLabel(SyncStatus.PENDING_SYNC))
        assertEquals("Upload failed", PendingSyncQueueBuilder.statusLabel(SyncStatus.SYNC_FAILED))
    }

    private fun syncedInspection(id: String) = Inspection(
        id = id,
        templateId = "tmpl-engine",
        apparatusId = "ap-engine-5",
        departmentId = "5",
        startedAt = 0,
        startedByUserId = "user-1",
        syncStatus = SyncStatus.SYNCED
    )

    private fun pendingInspection(id: String) = syncedInspection(id).copy(syncStatus = SyncStatus.PENDING_SYNC)
}
