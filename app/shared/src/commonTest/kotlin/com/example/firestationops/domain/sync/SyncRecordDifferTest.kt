package com.example.firestationops.domain.sync

import com.example.firestationops.domain.model.Apparatus
import com.example.firestationops.domain.model.ApparatusStatus
import com.example.firestationops.domain.model.Department
import com.example.firestationops.domain.model.Inspection
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import com.example.firestationops.domain.model.Station
import com.example.firestationops.domain.model.SyncStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncRecordDifferTest {
    @Test
    fun inspectionsMatch_ignoresSyncStatus() {
        val local = sampleInspection(syncStatus = SyncStatus.PENDING_SYNC)
        val remote = local.copy(syncStatus = SyncStatus.SYNCED)

        assertTrue(SyncRecordDiffer.inspectionsMatch(remote, local))
    }

    @Test
    fun inspectionsMatch_detectsContentChanges() {
        val local = sampleInspection()
        val remote = local.copy(isFinalized = true)

        assertFalse(SyncRecordDiffer.inspectionsMatch(remote, local))
    }

    @Test
    fun stationsMatch_ignoresAuditTimestamps() {
        val local = Station(
            id = "st-5",
            departmentId = "5",
            name = "Department 5 Station",
            address = "Calhoun, TN",
            createdAt = 1L,
            updatedAt = 2L
        )
        val remote = local.copy(createdAt = 99L, updatedAt = 100L)

        assertTrue(SyncRecordDiffer.stationsMatch(remote, local))
    }

    @Test
    fun membersMatch_ignoresAuditTimestampsAndEmailCase() {
        val local = Member(
            id = "uid-1",
            departmentId = "5",
            email = "Member@Example.com",
            firstName = "Chris",
            lastName = "Lefebvre",
            memberNumber = "221",
            roles = setOf(Role.ADMIN),
            createdAt = 1L,
            updatedAt = 2L
        )
        val remote = local.copy(email = "member@example.com", createdAt = 50L, updatedAt = 60L)

        assertTrue(SyncRecordDiffer.membersMatch(remote, local))
    }

    @Test
    fun departmentsMatch_detectsNameChanges() {
        val local = Department(id = "5", name = "Dept A", stationIds = listOf("st-5"))
        val remote = local.copy(name = "Dept B")

        assertFalse(SyncRecordDiffer.departmentsMatch(remote, local))
    }

    private fun sampleInspection(syncStatus: SyncStatus = SyncStatus.SYNCED) = Inspection(
        id = "insp-1",
        templateId = "tmpl-engine",
        apparatusId = "ap-engine-5",
        departmentId = "5",
        startedAt = 100L,
        startedByUserId = "user-1",
        syncStatus = syncStatus
    )
}
