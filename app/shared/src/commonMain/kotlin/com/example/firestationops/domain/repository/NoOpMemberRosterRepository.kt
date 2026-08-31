package com.example.firestationops.domain.repository

import com.example.firestationops.domain.membership.MemberRosterInput
import com.example.firestationops.domain.model.Member

class NoOpMemberRosterRepository : MemberRosterRepository {
    override suspend fun upsertMember(
        actingMember: Member,
        input: MemberRosterInput,
        editingMemberId: String?,
        assignedMemberId: String?
    ): Result<Member> = Result.failure(UnsupportedOperationException("Member roster management is not available."))

    override suspend fun setMemberActive(
        actingMember: Member,
        memberId: String,
        isActive: Boolean
    ): Result<Member> = Result.failure(UnsupportedOperationException("Member roster management is not available."))
}
