package com.example.firestationops.domain.membership

import com.example.firestationops.domain.model.Member

/**
 * Calhoun VFD legacy membership helpers.
 *
 * [normalize] is for trusted migration tooling and tests only. Production runtime code must not
 * rewrite canonical membership [Member.departmentId] values.
 */
object CalhounMembershipNormalizer {
    const val DEPARTMENT_NUMBER = "5"
    val MEMBER_NUMBER_RANGE = 200..225

    fun normalize(member: Member): Member {
        val parsed = member.departmentId.toIntOrNull() ?: return member
        if (parsed !in MEMBER_NUMBER_RANGE) return member

        return member.copy(
            departmentId = DEPARTMENT_NUMBER,
            memberNumber = member.memberNumber ?: member.departmentId
        )
    }

    fun normalizeDepartmentAndMemberNumber(
        departmentId: String,
        memberNumber: String?
    ): Pair<String, String?> {
        val parsed = departmentId.toIntOrNull()
        if (parsed != null && parsed in MEMBER_NUMBER_RANGE) {
            return DEPARTMENT_NUMBER to (memberNumber ?: departmentId)
        }
        return departmentId to memberNumber
    }

    fun isLegacyMemberNumberUsedAsDepartmentId(departmentId: String): Boolean {
        val parsed = departmentId.toIntOrNull() ?: return false
        return parsed in MEMBER_NUMBER_RANGE
    }
}
