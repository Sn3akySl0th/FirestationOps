package com.example.firestationops.data.firebase

import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.auth.PasswordResetRules
import com.example.firestationops.domain.membership.MemberProvisioningRules
import com.example.firestationops.domain.membership.MemberRosterInput
import com.example.firestationops.domain.membership.MemberRosterWrite
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.repository.MemberRosterAvailability
import com.example.firestationops.domain.repository.MemberRosterRepository

class FirebaseMemberRosterRepository(
    private val database: FirestationOpsDatabase,
    private val functionsClient: FirebaseMemberFunctionsGateway
) : MemberRosterRepository {
    override val availability: MemberRosterAvailability = MemberRosterAvailability.Available

    override suspend fun upsertMember(
        actingMember: Member,
        input: MemberRosterInput,
        editingMemberId: String?,
        assignedMemberId: String?
    ): Result<MemberRosterWrite> = runCatching {
        MemberProvisioningRules.requireAdmin(actingMember)?.let { error(it) }
        val normalizedInput = input.copy(
            email = MemberProvisioningRules.normalizeEmail(input.email),
            firstName = input.firstName.trim(),
            lastName = input.lastName.trim(),
            memberNumber = input.memberNumber?.trim()?.takeIf(String::isNotEmpty),
            initialPassword = input.initialPassword?.takeIf { it.isNotEmpty() }
        )
        MemberProvisioningRules.validateRosterInput(
            input = normalizedInput,
            departmentId = actingMember.departmentId,
            existingMembers = database.getAllMembersByDepartment(actingMember.departmentId),
            editingMemberId = editingMemberId
        )?.let { error(it) }

        val write = if (editingMemberId == null) {
            MemberProvisioningRules.validateInitialPassword(normalizedInput.initialPassword)?.let { error(it) }
            functionsClient.provisionMember(normalizedInput)
        } else {
            val member = functionsClient.updateMember(
                editingMemberId,
                normalizedInput.copy(initialPassword = null)
            )
            MemberRosterWrite(member = member)
        }
        require(write.member.departmentId == actingMember.departmentId) {
            "Member service returned a profile for another department."
        }
        database.upsertCanonicalMember(write.member)
        write
    }

    override suspend fun setMemberActive(
        actingMember: Member,
        memberId: String,
        isActive: Boolean
    ): Result<Member> = runCatching {
        MemberProvisioningRules.requireAdmin(actingMember)?.let { error(it) }
        val existing = database.getMemberById(memberId) ?: error("Member not found.")
        require(existing.departmentId == actingMember.departmentId) {
            "Member is not in your department."
        }

        val member = if (isActive) {
            functionsClient.updateMember(
                memberId,
                MemberRosterInput(
                    email = existing.email,
                    firstName = existing.firstName,
                    lastName = existing.lastName,
                    memberNumber = existing.memberNumber,
                    roles = existing.roles,
                    isActive = true
                )
            )
        } else {
            functionsClient.deactivateMember(memberId)
        }
        require(member.departmentId == actingMember.departmentId) {
            "Member service returned a profile for another department."
        }
        database.upsertCanonicalMember(member)
        member
    }

    override suspend fun sendPasswordReset(
        actingMember: Member,
        memberId: String
    ): Result<Unit> = runCatching {
        MemberProvisioningRules.requireAdmin(actingMember)?.let { error(it) }
        val existing = database.getMemberById(memberId) ?: error("Member not found.")
        require(existing.departmentId == actingMember.departmentId) {
            "Member is not in your department."
        }
        if (!existing.isActive) {
            error("Password reset is only available for active members.")
        }
        PasswordResetRules.validateEmail(existing.email)?.let { error(it) }
        functionsClient.sendPasswordReset(memberId)
    }
}
