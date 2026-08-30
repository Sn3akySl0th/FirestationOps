package com.example.firestationops.domain.sync

import com.example.firestationops.domain.model.Attachment
import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.DeficiencyStatus
import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncStatusTransitionsTest {
    @Test
    fun inspectionDraft_staysLocalOnly() {
        val inspection = Inspection(
            id = "insp-1",
            templateId = "tmpl-1",
            apparatusId = "ap-1",
            departmentId = "dept-1",
            startedAt = 1L,
            startedByUserId = "user-1",
            isFinalized = false
        )

        assertEquals(SyncStatus.LOCAL_ONLY, SyncStatusTransitions.inspectionForDraft(inspection).syncStatus)
    }

    @Test
    fun inspectionSubmit_marksPendingSync() {
        val inspection = Inspection(
            id = "insp-1",
            templateId = "tmpl-1",
            apparatusId = "ap-1",
            departmentId = "dept-1",
            startedAt = 1L,
            completedAt = 2L,
            startedByUserId = "user-1",
            isFinalized = true
        )

        assertEquals(SyncStatus.PENDING_SYNC, SyncStatusTransitions.inspectionForSubmit(inspection).syncStatus)
    }

    @Test
    fun attachmentSave_marksPendingSyncUnlessAlreadySynced() {
        val pending = Attachment(
            id = "att-1",
            departmentId = "dept-1",
            localUri = "/tmp/photo.jpg",
            createdAt = 1L,
            createdByUserId = "user-1"
        )
        val synced = pending.copy(syncStatus = SyncStatus.SYNCED, remoteUrl = "https://example.com/att-1")

        assertEquals(SyncStatus.PENDING_SYNC, SyncStatusTransitions.attachmentForSave(pending).syncStatus)
        assertEquals(SyncStatus.SYNCED, SyncStatusTransitions.attachmentForSave(synced).syncStatus)
    }

    @Test
    fun deficiencySave_marksPendingSync() {
        val deficiency = Deficiency(
            id = "def-1",
            apparatusId = "ap-1",
            departmentId = "dept-1",
            title = "Failed pump",
            description = "Low pressure",
            severity = DeficiencySeverity.REPAIR_NEEDED,
            status = DeficiencyStatus.OPEN,
            createdAt = 1L,
            createdByUserId = "user-1"
        )

        assertEquals(SyncStatus.PENDING_SYNC, SyncStatusTransitions.deficiencyForSave(deficiency).syncStatus)
    }
}
