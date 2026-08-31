package com.example.firestationops.domain.sync

import com.example.firestationops.domain.model.Deficiency
import com.example.firestationops.domain.model.DeficiencySeverity
import com.example.firestationops.domain.model.DeficiencyStatus
import com.example.firestationops.domain.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncConflictResolverTest {
    @Test
    fun resolveDeficiency_keepLocal_marksPendingSync() {
        val local = deficiency(status = DeficiencyStatus.RESOLVED)
        val remote = deficiency(status = DeficiencyStatus.OPEN)

        val resolved = SyncConflictResolver.resolveDeficiency(
            SyncConflictResolution.KEEP_LOCAL,
            local,
            remote
        )

        assertEquals(DeficiencyStatus.RESOLVED, resolved.status)
        assertEquals(SyncStatus.PENDING_SYNC, resolved.syncStatus)
    }

    @Test
    fun resolveDeficiency_keepRemote_marksSynced() {
        val local = deficiency(status = DeficiencyStatus.RESOLVED)
        val remote = deficiency(status = DeficiencyStatus.OPEN)

        val resolved = SyncConflictResolver.resolveDeficiency(
            SyncConflictResolution.KEEP_REMOTE,
            local,
            remote
        )

        assertEquals(DeficiencyStatus.OPEN, resolved.status)
        assertEquals(SyncStatus.SYNCED, resolved.syncStatus)
    }

    private fun deficiency(status: DeficiencyStatus): Deficiency =
        Deficiency(
            id = "def-1",
            apparatusId = "ap-1",
            departmentId = "dept-1",
            title = "Pump leak",
            description = "Needs repair",
            severity = DeficiencySeverity.REPAIR_NEEDED,
            status = status,
            createdAt = 1_000L,
            createdByUserId = "member-1"
        )
}
