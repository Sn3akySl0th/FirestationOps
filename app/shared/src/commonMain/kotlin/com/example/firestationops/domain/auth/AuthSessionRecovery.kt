package com.example.firestationops.domain.auth

import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.membership.CalhounMembershipNormalizer
import com.example.firestationops.domain.membership.MemberProvisioningRules
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.UserState

object AuthSessionRecovery {
    fun recoverLocalSession(database: FirestationOpsDatabase): UserState {
        val userId = database.getSessionUserId() ?: return UserState.Unauthenticated
        val member = database.getMemberById(userId)?.let(CalhounMembershipNormalizer::normalize)
            ?: run {
                database.setSessionUserId(null)
                return UserState.Unauthenticated
            }

        MemberProvisioningRules.validateMemberProfile(member)?.let { message ->
            return UserState.Error(message)
        }

        return UserState.Authenticated(member)
    }

    fun activateMember(database: FirestationOpsDatabase, member: Member): UserState {
        val normalized = CalhounMembershipNormalizer.normalize(member)
        MemberProvisioningRules.validateMemberProfile(normalized)?.let { message ->
            return UserState.Error(message)
        }
        database.setSessionUserId(normalized.id)
        return UserState.Authenticated(normalized)
    }
}
