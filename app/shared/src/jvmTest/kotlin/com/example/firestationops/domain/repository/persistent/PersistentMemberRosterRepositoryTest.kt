package com.example.firestationops.domain.repository.persistent

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.db.FirestationOpsDb
import com.example.firestationops.domain.membership.MemberRosterInput
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersistentMemberRosterRepositoryTest {
    @Test
    fun upsertMember_preservesActingMemberDepartmentIdWithoutNormalization() = runTest {
        val database = createDatabase()
        val repository = PersistentMemberRosterRepository(database)
        val admin = Member(
            id = "admin-221",
            departmentId = "221",
            email = "admin@example.test",
            firstName = "Admin",
            lastName = "User",
            roles = setOf(Role.ADMIN),
        )
        database.upsertCanonicalMember(admin)

        val result = repository.upsertMember(
            actingMember = admin,
            input = MemberRosterInput(
                email = "member@example.test",
                firstName = "New",
                lastName = "Member",
                memberNumber = "221",
                roles = setOf(Role.MEMBER),
                isActive = true,
            ),
            editingMemberId = null,
            assignedMemberId = null,
        )

        assertTrue(result.isSuccess)
        assertEquals("221", result.getOrThrow().member.departmentId)
        assertEquals("221", database.getAllMembersByDepartment("221").single { it.email == "member@example.test" }.departmentId)
    }

    private fun createDatabase(): FirestationOpsDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FirestationOpsDb.Schema.create(driver)
        return FirestationOpsDatabase(driver)
    }
}
