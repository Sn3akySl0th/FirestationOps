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
    fun canAutoProvisionFromLocal_requiresMatchingEmail() {
        val localMember = Member(
            id = "local-id",
            departmentId = "dept-1",
            email = "admin@example.com",
            firstName = "Admin",
            lastName = "User"
        )

        assertTrue(MemberProvisioningRules.canAutoProvisionFromLocal(localMember, "admin@example.com"))
        assertFalse(MemberProvisioningRules.canAutoProvisionFromLocal(localMember, "other@example.com"))
        assertFalse(MemberProvisioningRules.canAutoProvisionFromLocal(null, "admin@example.com"))
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
}
