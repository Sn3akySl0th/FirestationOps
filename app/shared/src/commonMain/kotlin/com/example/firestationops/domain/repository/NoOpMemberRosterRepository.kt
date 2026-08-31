package com.example.firestationops.domain.repository

import com.example.firestationops.domain.membership.MemberRosterInput
import com.example.firestationops.domain.model.Member

class NoOpMemberRosterRepository(
    private val explanation: String = "Member roster management is not available."
) : MemberRosterRepository {
    override val availability: MemberRosterAvailability =
        MemberRosterAvailability.Unavailable(explanation)

    override suspend fun upsertMember(
        actingMember: Member,
        input: MemberRosterInput,
        editingMemberId: String?,
        assignedMemberId: String?
    ): Result<Member> = Result.failure(UnsupportedOperationException(explanation))

    override suspend fun setMemberActive(
        actingMember: Member,
        memberId: String,
        isActive: Boolean
    ): Result<Member> = Result.failure(UnsupportedOperationException(explanation))
}
