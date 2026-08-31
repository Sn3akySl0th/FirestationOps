package com.example.firestationops.data.firebase

import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.membership.MemberProvisioningRules
import com.example.firestationops.domain.membership.MemberRosterInput
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.repository.MemberRosterRepository
import com.example.firestationops.domain.repository.persistent.PersistentMemberRosterRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.tasks.await

class FirebaseMemberRosterRepository(
    private val local: PersistentMemberRosterRepository,
    private val database: FirestationOpsDatabase,
    private val firebaseEnabled: Boolean,
    private val accountProvisioner: FirebaseMemberAccountProvisioner,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : MemberRosterRepository {
    override suspend fun upsertMember(
        actingMember: Member,
        input: MemberRosterInput,
        editingMemberId: String?,
        assignedMemberId: String?
    ): Result<Member> {
        val resolvedMemberId = when {
            editingMemberId != null -> assignedMemberId
            firebaseEnabled -> {
                MemberProvisioningRules.validateInitialPassword(input.initialPassword)?.let {
                    return Result.failure(IllegalArgumentException(it))
                }
                accountProvisioner
                    .createSignInAccount(input.email, input.initialPassword!!)
                    .getOrElse { return Result.failure(it) }
            }
            else -> assignedMemberId
        }

        val previousMember = editingMemberId?.let(database::getMemberById)
        val localResult = local.upsertMember(
            actingMember = actingMember,
            input = input,
            editingMemberId = editingMemberId,
            assignedMemberId = resolvedMemberId
        )
        if (localResult.isFailure || !firebaseEnabled) {
            return localResult
        }

        val member = localResult.getOrThrow()
        return runCatching {
            mirrorMemberToCloud(member, previousMember)
            member
        }.recoverCatching { error ->
            rollbackLocalMember(member, previousMember, editingMemberId)
            throw mapCloudError(error)
        }
    }

    override suspend fun setMemberActive(
        actingMember: Member,
        memberId: String,
        isActive: Boolean
    ): Result<Member> {
        val previousMember = database.getMemberById(memberId)
        val localResult = local.setMemberActive(actingMember, memberId, isActive)
        if (localResult.isFailure || !firebaseEnabled) {
            return localResult
        }

        val member = localResult.getOrThrow()
        return runCatching {
            mirrorMemberToCloud(member, previousMember)
            member
        }.recoverCatching { error ->
            rollbackLocalMember(member, previousMember, memberId)
            throw mapCloudError(error)
        }
    }

    private fun rollbackLocalMember(
        savedMember: Member,
        previousMember: Member?,
        editingMemberId: String?
    ) {
        if (editingMemberId == null) {
            database.deleteMemberById(savedMember.id)
        } else if (previousMember != null) {
            database.upsertCanonicalMember(previousMember)
        }
    }

    private suspend fun mirrorMemberToCloud(member: Member, previousMember: Member?) {
        val batch = firestore.batch()

        batch.set(
            firestore.document(FirestorePaths.departmentMember(member.departmentId, member.id)),
            FirestoreMappers.memberToMap(member),
            SetOptions.merge()
        )

        if (!MemberProvisioningRules.isPendingMemberId(member.id)) {
            batch.set(
                firestore.document(FirestorePaths.member(member.id)),
                FirestoreMappers.memberToMap(member),
                SetOptions.merge()
            )
        }

        if (MemberProvisioningRules.isPendingMemberId(member.id) && member.isActive) {
            batch.set(
                firestore.document(FirestorePaths.memberInvite(member.email)),
                FirestoreMappers.memberInviteToMap(member, pendingMemberId = member.id),
                SetOptions.merge()
            )
        } else {
            queueInviteDelete(batch, member.email)
        }

        previousMember?.let { previous ->
            val emailChanged = MemberProvisioningRules.normalizeEmail(previous.email) !=
                MemberProvisioningRules.normalizeEmail(member.email)
            if (emailChanged) {
                queueInviteDelete(batch, previous.email)
            }

            if (previous.id != member.id &&
                MemberProvisioningRules.isPendingMemberId(previous.id)
            ) {
                batch.delete(
                    firestore.document(FirestorePaths.departmentMember(previous.departmentId, previous.id))
                )
            }
        }

        batch.commit().await()
    }

    private fun queueInviteDelete(batch: WriteBatch, email: String) {
        batch.delete(firestore.document(FirestorePaths.memberInvite(email)))
    }

    private fun mapCloudError(error: Throwable): Throwable {
        if (error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return IllegalStateException(
                "Cloud permission denied. Deploy the latest Firestore rules from this repository, " +
                    "then sign out and sign back in so your admin profile uses department 5.",
                error
            )
        }
        return error
    }
}
