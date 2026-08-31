package com.example.firestationops.domain.membership

import com.example.firestationops.domain.model.Member

object MemberProvisioningRules {
    fun validateMemberProfile(member: Member): String? {
        if (member.departmentId.isBlank()) {
            return "No department assigned to your account. Contact your department administrator."
        }
        if (!member.isActive) {
            return "Your account is inactive. Contact your department administrator."
        }
        return null
    }

    fun canAutoProvisionFromLocal(localMember: Member?, email: String): Boolean =
        localMember != null && localMember.email.equals(email, ignoreCase = true)

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
}
