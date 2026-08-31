package com.example.firestationops.domain.membership

import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalhounMembershipNormalizerTest {
    @Test
    fun normalize_mapsBadgeNumber221ToDepartment5() {
        val member = Member(
            id = "uid-1",
            departmentId = "221",
            email = "member@example.com",
            firstName = "Chris",
            lastName = "Lefebvre",
            roles = setOf(Role.ADMIN)
        )

        val normalized = CalhounMembershipNormalizer.normalize(member)

        assertEquals("5", normalized.departmentId)
        assertEquals("221", normalized.memberNumber)
    }

    @Test
    fun normalize_preservesExplicitDepartmentNumber() {
        val member = Member(
            id = "uid-1",
            departmentId = "5",
            memberNumber = "221",
            email = "member@example.com",
            firstName = "Chris",
            lastName = "Lefebvre"
        )

        val normalized = CalhounMembershipNormalizer.normalize(member)

        assertEquals("5", normalized.departmentId)
        assertEquals("221", normalized.memberNumber)
    }

    @Test
    fun isLegacyMemberNumberUsedAsDepartmentId_detectsBadgeRange() {
        assertTrue(CalhounMembershipNormalizer.isLegacyMemberNumberUsedAsDepartmentId("221"))
        assertFalse(CalhounMembershipNormalizer.isLegacyMemberNumberUsedAsDepartmentId("5"))
    }
}
