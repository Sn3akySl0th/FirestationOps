package com.example.firestationops.domain.repository

import com.example.firestationops.domain.membership.MemberRosterInput
import com.example.firestationops.domain.model.Member

interface MemberRosterRepository {
    suspend fun upsertMember(
        actingMember: Member,
        input: MemberRosterInput,
        editingMemberId: String? = null,
        assignedMemberId: String? = null
    ): Result<Member>

    suspend fun setMemberActive(
        actingMember: Member,
        memberId: String,
        isActive: Boolean
    ): Result<Member>
}
