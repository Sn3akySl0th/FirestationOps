package com.example.firestationops.domain.repository.persistent

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.membership.CalhounMembershipNormalizer
import com.example.firestationops.domain.membership.MemberProvisioningRules
import com.example.firestationops.domain.membership.MemberRosterInput
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.repository.MemberRosterRepository
import com.example.firestationops.domain.repository.MemberRosterAvailability
import com.example.firestationops.randomUUID

class PersistentMemberRosterRepository(
    private val database: FirestationOpsDatabase
) : MemberRosterRepository {
    override val availability: MemberRosterAvailability = MemberRosterAvailability.Available

    override suspend fun upsertMember(
        actingMember: Member,
        input: MemberRosterInput,
        editingMemberId: String?,
        assignedMemberId: String?
    ): Result<Member> = runCatching {
        MemberProvisioningRules.requireAdmin(actingMember)?.let { error(it) }

        val existingMembers = database.getAllMembersByDepartment(actingMember.departmentId)
        val normalizedInput = input.copy(
            email = MemberProvisioningRules.normalizeEmail(input.email),
            firstName = input.firstName.trim(),
            lastName = input.lastName.trim(),
            memberNumber = input.memberNumber?.trim()?.takeIf { it.isNotEmpty() },
            initialPassword = input.initialPassword?.trim()?.takeIf { it.isNotEmpty() }
        )
        MemberProvisioningRules.validateRosterInput(
            input = normalizedInput,
            departmentId = actingMember.departmentId,
            existingMembers = existingMembers,
            editingMemberId = editingMemberId
        )?.let { error(it) }

        val now = currentTimeMillis()
        val existing = editingMemberId?.let { database.getMemberById(it) }
        val memberId = when {
            existing != null -> existing.id
            !assignedMemberId.isNullOrBlank() -> assignedMemberId
            else -> "${MemberProvisioningRules.PENDING_MEMBER_ID_PREFIX}${randomUUID()}"
        }
        val member = CalhounMembershipNormalizer.normalize(
            Member(
                id = memberId,
                departmentId = actingMember.departmentId,
                email = normalizedInput.email,
                firstName = normalizedInput.firstName,
                lastName = normalizedInput.lastName,
                memberNumber = normalizedInput.memberNumber,
                roles = normalizedInput.roles,
                isActive = normalizedInput.isActive,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )

        database.upsertCanonicalMember(member)
        member
    }

    override suspend fun setMemberActive(
        actingMember: Member,
        memberId: String,
        isActive: Boolean
    ): Result<Member> = runCatching {
        MemberProvisioningRules.requireAdmin(actingMember)?.let { error(it) }

        val existing = database.getMemberById(memberId)
            ?: error("Member not found.")
        if (existing.departmentId != actingMember.departmentId) {
            error("Member is not in your department.")
        }

        val departmentMembers = database.getAllMembersByDepartment(actingMember.departmentId)
        val input = MemberRosterInput(
            email = existing.email,
            firstName = existing.firstName,
            lastName = existing.lastName,
            memberNumber = existing.memberNumber,
            roles = existing.roles,
            isActive = isActive
        )
        if (!MemberProvisioningRules.canChangeMemberActivation(existing, input, departmentMembers)) {
            error("The department must keep at least one active administrator.")
        }

        val updated = existing.copy(
            isActive = isActive,
            updatedAt = currentTimeMillis()
        )
        database.upsertCanonicalMember(updated)
        updated
    }
}
