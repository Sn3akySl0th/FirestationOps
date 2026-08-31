package com.example.firestationops.domain.membership

import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemberProvisioningRulesTest {
    @Test
    fun validateMemberProfile_rejectsBlankDepartment() {
        val member = Member(
            id = "uid-1",
            departmentId = "",
            email = "member@example.com",
            firstName = "Alex",
            lastName = "Rivera"
        )

        assertEquals(
            "No department assigned to your account. Contact your department administrator.",
            MemberProvisioningRules.validateMemberProfile(member)
        )
    }

    @Test
    fun validateMemberProfile_rejectsInactiveMember() {
        val member = Member(
            id = "uid-1",
            departmentId = "dept-1",
            email = "member@example.com",
            firstName = "Alex",
            lastName = "Rivera",
            isActive = false
        )

        assertEquals(
            "Your account is inactive. Contact your department administrator.",
            MemberProvisioningRules.validateMemberProfile(member)
        )
    }

    @Test
    fun validateMemberProfile_acceptsActiveMemberWithDepartment() {
        val member = Member(
            id = "uid-1",
            departmentId = "dept-1",
            email = "member@example.com",
            firstName = "Alex",
            lastName = "Rivera",
            roles = setOf(Role.MEMBER)
        )

        assertNull(MemberProvisioningRules.validateMemberProfile(member))
    }

    @Test
    fun validateMemberProfile_rejectsEmptyRoles() {
        val member = Member(
            id = "uid-1",
            departmentId = "dept-1",
            email = "member@example.com",
            firstName = "Alex",
            lastName = "Rivera",
            roles = emptySet()
        )

        assertEquals(
            "Your member profile has invalid roles. Contact your department administrator.",
            MemberProvisioningRules.validateMemberProfile(member)
        )
    }

    @Test
    fun parseCanonicalRoles_rejectsMalformedRoleLists() {
        assertNull(MemberProvisioningRules.parseCanonicalRoles(emptyList<String>()))
        assertNull(MemberProvisioningRules.parseCanonicalRoles(listOf("UNKNOWN")))
        assertNull(MemberProvisioningRules.parseCanonicalRoles(listOf("ADMIN", "UNKNOWN")))
        assertNull(MemberProvisioningRules.parseCanonicalRoles("ADMIN"))
        assertNull(MemberProvisioningRules.parseCanonicalRoles(null))
    }

    @Test
    fun deduplicateMembersByEmail_prefersFirebaseMemberOverLocalPlaceholder() {
        val localPlaceholder = Member(
            id = "user-clefebvre-id",
            departmentId = "5",
            memberNumber = "221",
            email = "clefebvre81@gmail.com",
            firstName = "Chris",
            lastName = "Lefebvre"
        )
        val firebaseMember = localPlaceholder.copy(id = "firebase-uid-abc123")

        val deduped = MemberProvisioningRules.deduplicateMembersByEmail(
            listOf(localPlaceholder, firebaseMember)
        )

        assertEquals(1, deduped.size)
        assertEquals("firebase-uid-abc123", deduped.single().id)
    }

    @Test
    fun isLocalDevelopmentMemberId_detectsSeededPlaceholderIds() {
        assertTrue(MemberProvisioningRules.isLocalDevelopmentMemberId("user-clefebvre-id"))
        assertTrue(MemberProvisioningRules.isLocalDevelopmentMemberId("admin-user-id"))
        assertFalse(MemberProvisioningRules.isLocalDevelopmentMemberId("firebase-uid-abc123"))
    }

    @Test
    fun isPendingMemberId_detectsPendingRosterEntries() {
        assertTrue(MemberProvisioningRules.isPendingMemberId("pending-abc-123"))
        assertFalse(MemberProvisioningRules.isPendingMemberId("firebase-uid-abc123"))
    }

    @Test
    fun validateRosterInput_rejectsDuplicateEmail() {
        val existing = Member(
            id = "member-1",
            departmentId = "5",
            email = "member@example.com",
            firstName = "Alex",
            lastName = "Rivera"
        )
        val input = MemberRosterInput(
            email = "member@example.com",
            firstName = "Jamie",
            lastName = "Lee"
        )

        assertEquals(
            "A member with this email already exists in the department.",
            MemberProvisioningRules.validateRosterInput(input, "5", listOf(existing))
        )
    }

    @Test
    fun validateRosterInput_rejectsCalhounBadgeOutsideRange() {
        val input = MemberRosterInput(
            email = "new@example.com",
            firstName = "Jamie",
            lastName = "Lee",
            memberNumber = "199"
        )

        assertEquals(
            "Member number must be between 200 and 225.",
            MemberProvisioningRules.validateRosterInput(input, "5", emptyList())
        )
    }

    @Test
    fun canChangeMemberActivation_blocksDeactivatingLastAdmin() {
        val admin = Member(
            id = "admin-1",
            departmentId = "5",
            email = "admin@example.com",
            firstName = "Admin",
            lastName = "User",
            roles = setOf(Role.ADMIN)
        )
        val input = MemberRosterInput(
            email = admin.email,
            firstName = admin.firstName,
            lastName = admin.lastName,
            roles = admin.roles,
            isActive = false
        )

        assertFalse(
            MemberProvisioningRules.canChangeMemberActivation(admin, input, listOf(admin))
        )
    }

    @Test
    fun validateInitialPassword_requiresAtLeastSixCharacters() {
        assertEquals(
            "Initial password must be at least 6 characters.",
            MemberProvisioningRules.validateInitialPassword("12345")
        )
        assertNull(MemberProvisioningRules.validateInitialPassword("123456"))
    }

    @Test
    fun requireAdmin_rejectsNonAdminActor() {
        val member = Member(
            id = "member-1",
            departmentId = "5",
            email = "member@example.com",
            firstName = "Alex",
            lastName = "Rivera",
            roles = setOf(Role.MEMBER)
        )

        assertEquals(
            "Only administrators can manage the member roster.",
            MemberProvisioningRules.requireAdmin(member)
        )
    }
}
