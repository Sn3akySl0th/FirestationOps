package com.example.firestationops.data.firebase

import com.example.firestationops.domain.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FirestoreMappersMemberTest {
    private fun validMemberData(
        departmentId: String = "221",
        roles: Any? = listOf("ADMIN"),
        isActive: Boolean = true,
    ): Map<String, Any?> = mapOf(
        "departmentId" to departmentId,
        "email" to "member@example.test",
        "firstName" to "Chris",
        "lastName" to "Lefebvre",
        "memberNumber" to "221",
        "roles" to roles,
        "isActive" to isActive,
        "createdAt" to 1L,
        "updatedAt" to 2L,
    )

    @Test
    fun memberFromMap_preservesCanonicalDepartmentIdUnchanged() {
        val member = FirestoreMappers.memberFromMap(
            id = "uid-221",
            data = validMemberData(),
        )

        assertNotNull(member)
        assertEquals("221", member.departmentId)
        assertEquals("221", member.memberNumber)
        assertEquals(setOf(Role.ADMIN), member.roles)
    }

    @Test
    fun memberFromMap_rejectsEmptyUnknownMixedScalarAndAbsentRoles() {
        assertNull(FirestoreMappers.memberFromMap("uid-1", validMemberData(roles = emptyList<String>())))
        assertNull(FirestoreMappers.memberFromMap("uid-2", validMemberData(roles = listOf("UNKNOWN"))))
        assertNull(FirestoreMappers.memberFromMap("uid-3", validMemberData(roles = listOf("ADMIN", "UNKNOWN"))))
        assertNull(FirestoreMappers.memberFromMap("uid-4", validMemberData(roles = "ADMIN")))
        assertNull(FirestoreMappers.memberFromMap("uid-5", validMemberData().minus("roles")))
    }

    @Test
    fun memberFromMap_rejectsMissingIsActive() {
        assertNull(
            FirestoreMappers.memberFromMap(
                id = "uid-6",
                data = validMemberData().minus("isActive"),
            )
        )
    }
}
