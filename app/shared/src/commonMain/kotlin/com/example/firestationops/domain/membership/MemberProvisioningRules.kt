package com.example.firestationops.domain.membership

import com.example.firestationops.domain.membership.CalhounMembershipNormalizer.MEMBER_NUMBER_RANGE
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role

object MemberProvisioningRules {
    private val emailPattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

    const val PENDING_MEMBER_ID_PREFIX = "pending-"

    fun hasValidCanonicalRoles(roles: Set<Role>): Boolean = roles.isNotEmpty()

    fun parseCanonicalRoles(rolesRaw: Any?): Set<Role>? {
        if (rolesRaw !is List<*>) return null
        if (rolesRaw.isEmpty()) return null
        val roles = LinkedHashSet<Role>()
        for (item in rolesRaw) {
            val roleName = item as? String ?: return null
            val role = runCatching { Role.valueOf(roleName) }.getOrElse { return null }
            roles.add(role)
        }
        return roles
    }

    fun validateMemberProfile(member: Member): String? {
        if (member.departmentId.isBlank()) {
            return "No department assigned to your account. Contact your department administrator."
        }
        if (!hasValidCanonicalRoles(member.roles)) {
            return "Your member profile has invalid roles. Contact your department administrator."
        }
        if (!member.isActive) {
            return "Your account is inactive. Contact your department administrator."
        }
        return null
    }

    fun membershipRequiredMessage(): String =
        "No department membership found. Ask an administrator to create your member profile before signing in."

    fun isLocalDevelopmentMemberId(memberId: String): Boolean =
        memberId == "admin-user-id" ||
            memberId == "user-clefebvre-id" ||
            memberId.startsWith("user-")

    fun deduplicateMembersByEmail(members: List<Member>): List<Member> =
        members.groupBy { it.email.trim().lowercase() }
            .map { (_, duplicates) ->
                duplicates.singleOrNull { !isLocalDevelopmentMemberId(it.id) } ?: duplicates.first()
            }

    fun requireAdmin(actor: Member): String? =
        if (actor.hasRole(Role.ADMIN)) {
            null
        } else {
            "Only administrators can manage the member roster."
        }

    fun isPendingMemberId(memberId: String): Boolean =
        memberId.startsWith(PENDING_MEMBER_ID_PREFIX)

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun validateInitialPassword(password: String?): String? {
        val value = password?.trim().orEmpty()
        if (value.length < 6) {
            return "Initial password must be at least 6 characters."
        }
        return null
    }

    fun validateRosterInput(
        input: MemberRosterInput,
        departmentId: String,
        existingMembers: List<Member>,
        editingMemberId: String? = null
    ): String? {
        val email = normalizeEmail(input.email)
        if (email.isBlank() || !emailPattern.matches(email)) {
            return "Enter a valid email address."
        }
        if (input.firstName.isBlank()) {
            return "First name is required."
        }
        if (input.lastName.isBlank()) {
            return "Last name is required."
        }
        if (input.roles.isEmpty()) {
            return "Select at least one role."
        }

        val duplicateEmail = existingMembers.any { member ->
            member.id != editingMemberId &&
                normalizeEmail(member.email) == email
        }
        if (duplicateEmail) {
            return "A member with this email already exists in the department."
        }

        input.memberNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { memberNumber ->
            val parsed = memberNumber.toIntOrNull()
            if (departmentId == CalhounMembershipNormalizer.DEPARTMENT_NUMBER &&
                (parsed == null || parsed !in MEMBER_NUMBER_RANGE)
            ) {
                return "Member number must be between ${MEMBER_NUMBER_RANGE.first} and ${MEMBER_NUMBER_RANGE.last}."
            }
        }

        val normalizedInput = input.copy(
            email = email,
            firstName = input.firstName.trim(),
            lastName = input.lastName.trim(),
            memberNumber = input.memberNumber?.trim()?.takeIf { it.isNotEmpty() }
        )

        if (editingMemberId != null) {
            val existing = existingMembers.firstOrNull { it.id == editingMemberId }
                ?: return "Member not found."
            if (!canChangeMemberActivation(existing, normalizedInput, existingMembers)) {
                return "The department must keep at least one active administrator."
            }
            if (!canChangeMemberRoles(existing, normalizedInput, existingMembers)) {
                return "The department must keep at least one active administrator."
            }
        }

        return null
    }

    fun canChangeMemberActivation(
        existing: Member,
        input: MemberRosterInput,
        departmentMembers: List<Member>
    ): Boolean {
        if (input.isActive || !existing.isActive) return true
        if (!existing.hasRole(Role.ADMIN)) return true
        return countActiveAdmins(departmentMembers, excludingMemberId = existing.id) > 0
    }

    fun canChangeMemberRoles(
        existing: Member,
        input: MemberRosterInput,
        departmentMembers: List<Member>
    ): Boolean {
        val wasAdmin = existing.hasRole(Role.ADMIN)
        val willBeAdmin = input.roles.contains(Role.ADMIN)
        if (!wasAdmin || willBeAdmin) return true
        if (!input.isActive) return true
        return countActiveAdmins(departmentMembers, excludingMemberId = existing.id) > 0
    }

    fun countActiveAdmins(members: List<Member>, excludingMemberId: String? = null): Int =
        members.count { member ->
            member.id != excludingMemberId &&
                member.isActive &&
                member.hasRole(Role.ADMIN)
        }
}
