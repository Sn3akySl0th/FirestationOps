package com.example.firestationops.domain.auth

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.db.FirestationOpsDb
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import com.example.firestationops.domain.model.UserState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FirebaseBackedMembershipPersistenceTest {
    @Test
    fun upsertCanonicalMember_preservesCanonicalDepartmentId() {
        val database = createDatabase()
        val member = Member(
            id = "uid-221",
            departmentId = "221",
            email = "member@example.test",
            firstName = "Chris",
            lastName = "Lefebvre",
            memberNumber = "221",
            roles = setOf(Role.ADMIN),
        )

        database.upsertCanonicalMember(member)

        assertEquals("221", database.getMemberById("uid-221")?.departmentId)
        assertEquals("221", database.getMemberById("uid-221")?.memberNumber)
    }

    @Test
    fun activateMember_preservesCanonicalDepartmentIdInSession() {
        val database = createDatabase()
        val member = Member(
            id = "uid-221",
            departmentId = "221",
            email = "member@example.test",
            firstName = "Chris",
            lastName = "Lefebvre",
            memberNumber = "221",
            roles = setOf(Role.ADMIN),
        )
        database.upsertCanonicalMember(member)

        val state = AuthSessionRecovery.activateMember(database, member)

        assertIs<UserState.Authenticated>(state)
        assertEquals("221", state.member.departmentId)
        assertEquals("uid-221", database.getSessionUserId())
        assertEquals("221", database.getMemberById("uid-221")?.departmentId)
    }

    @Test
    fun activateMember_rejectsInvalidCanonicalRoles() {
        val database = createDatabase()
        val member = Member(
            id = "uid-invalid",
            departmentId = "dept-1",
            email = "member@example.test",
            firstName = "Chris",
            lastName = "Lefebvre",
            roles = emptySet(),
        )
        database.upsertCanonicalMember(member)

        val state = AuthSessionRecovery.activateMember(database, member)

        assertIs<UserState.Error>(state)
        assertEquals(
            "Your member profile has invalid roles. Contact your department administrator.",
            state.message,
        )
    }

    private fun createDatabase(): FirestationOpsDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        FirestationOpsDb.Schema.create(driver)
        return FirestationOpsDatabase(driver)
    }
}
