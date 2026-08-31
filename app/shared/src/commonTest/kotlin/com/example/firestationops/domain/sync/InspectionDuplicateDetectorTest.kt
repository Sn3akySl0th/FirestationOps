package com.example.firestationops.domain.sync

import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.InspectionResponse
import com.example.firestationops.domain.model.InspectionStatus
import com.example.firestationops.domain.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InspectionDuplicateDetectorTest {
    @Test
    fun findDuplicate_sameApparatusAndTemplateWithinWindow_returnsRemote() {
        val local = inspection(id = "insp-local", completedAt = 10_000L)
        val remote = inspection(id = "insp-remote", completedAt = 12_000L)

        val duplicate = InspectionDuplicateDetector.findDuplicate(
            local = local,
            remoteInspections = listOf(remote),
            frequencyHours = 24
        )

        assertEquals(remote, duplicate)
    }

    @Test
    fun findDuplicate_outsideFrequencyWindow_returnsNull() {
        val local = inspection(id = "insp-local", completedAt = 10_000L)
        val remote = inspection(id = "insp-remote", completedAt = 10_000L + (25 * 3_600_000L))

        val duplicate = InspectionDuplicateDetector.findDuplicate(
            local = local,
            remoteInspections = listOf(remote),
            frequencyHours = 24
        )

        assertNull(duplicate)
    }

    @Test
    fun findDuplicate_voidedRemoteIsIgnored() {
        val local = inspection(id = "insp-local", completedAt = 10_000L)
        val remote = inspection(id = "insp-remote", completedAt = 12_000L, voidedAt = 9_000L)

        val duplicate = InspectionDuplicateDetector.findDuplicate(
            local = local,
            remoteInspections = listOf(remote),
            frequencyHours = 24
        )

        assertNull(duplicate)
    }

    @Test
    fun findDuplicate_sameIdIsIgnored() {
        val local = inspection(id = "insp-1", completedAt = 10_000L)

        val duplicate = InspectionDuplicateDetector.findDuplicate(
            local = local,
            remoteInspections = listOf(local),
            frequencyHours = 24
        )

        assertNull(duplicate)
    }

    private fun inspection(
        id: String,
        completedAt: Long,
        voidedAt: Long? = null
    ): Inspection = Inspection(
        id = id,
        templateId = "tmpl-1",
        apparatusId = "ap-1",
        departmentId = "dept-1",
        startedAt = completedAt - 1_000L,
        completedAt = completedAt,
        startedByUserId = "member-1",
        responses = listOf(
            InspectionResponse(itemId = "item-1", status = InspectionStatus.FAIL, note = "Low")
        ),
        isFinalized = true,
        syncStatus = SyncStatus.PENDING_SYNC,
        voidedAt = voidedAt
    )
}
