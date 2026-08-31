package com.example.firestationops.data.firebase

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.auth.AuthSessionRecovery
import com.example.firestationops.domain.bootstrap.DemoDepartmentSeeder
import com.example.firestationops.domain.bootstrap.DepartmentCatalogProfiles
import com.example.firestationops.domain.membership.CalhounMembershipNormalizer
import com.example.firestationops.domain.membership.MemberProvisioningRules
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import com.example.firestationops.domain.model.UserState
import com.example.firestationops.domain.repository.AuthRepository
import com.example.firestationops.domain.repository.persistent.PersistentAuthRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class JvmFirebaseAuthRepository(
    private val database: FirestationOpsDatabase,
    private val localAuth: PersistentAuthRepository,
    private val firebaseEnabled: Boolean,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : AuthRepository {

    private val _userState = MutableStateFlow<UserState>(UserState.Unauthenticated)
    override val userState: StateFlow<UserState> = _userState.asStateFlow()

    init {
        if (!firebaseEnabled) {
            mirrorLocalAuthState()
        } else {
            recoverFirebaseSession()
        }
    }

    private fun mirrorLocalAuthState() {
        _userState.value = localAuth.userState.value
    }

    private fun recoverFirebaseSession() {
        val firebaseUser = auth.currentUser
        if (firebaseUser != null) {
            val cachedMember = database.getMemberById(firebaseUser.uid)?.let(CalhounMembershipNormalizer::normalize)
            if (cachedMember != null) {
                _userState.value = AuthSessionRecovery.activateMember(database, cachedMember)
                return
            }
        }

        _userState.value = AuthSessionRecovery.recoverLocalSession(database)
    }

    override suspend fun login(email: String, password: String): Result<Unit> {
        if (!firebaseEnabled) {
            val result = localAuth.login(email, password)
            mirrorLocalAuthState()
            return result
        }

        _userState.value = UserState.Loading
        val normalizedEmail = email.trim().lowercase()

        return runCatching {
            auth.signInWithEmailAndPassword(normalizedEmail, password).await()
            val firebaseUser = auth.currentUser ?: error("Firebase sign-in did not return a user.")
            val member = loadOrProvisionMember(firebaseUser.uid, normalizedEmail)
            MemberProvisioningRules.validateMemberProfile(member)?.let { message ->
                error(message)
            }
            database.upsertCanonicalMember(member)
            database.setSessionUserId(member.id)
            if (DepartmentCatalogProfiles.profileFor(member.departmentId) != null) {
                DemoDepartmentSeeder.ensureDemoData(database, member.departmentId)
            }
            _userState.value = UserState.Authenticated(member)
        }.onFailure { error ->
            _userState.value = UserState.Error(error.message ?: "Sign-in failed.")
        }.map { }
    }

    override suspend fun logout(): Result<Unit> {
        if (firebaseEnabled) {
            auth.signOut()
        } else {
            localAuth.logout()
        }
        database.setSessionUserId(null)
        _userState.value = UserState.Unauthenticated
        return Result.success(Unit)
    }

    private suspend fun loadOrProvisionMember(uid: String, email: String): Member {
        val snapshot = JvmGoogleFirestoreClient.getDocument(FirestorePaths.member(uid))
        if (snapshot.exists) {
            val rawData = snapshot.data
            val member = FirestoreMappers.memberFromMap(uid, rawData)
                ?: error("Member profile is missing required fields.")
            return finalizeMember(member, rawData["departmentId"] as? String)
        }

        val localMember = database.getMemberByEmail(email)?.let(CalhounMembershipNormalizer::normalize)
        if (MemberProvisioningRules.canAutoProvisionFromLocal(localMember, email)) {
            val now = currentTimeMillis()
            val member = CalhounMembershipNormalizer.normalize(
                localMember!!.copy(
                    id = uid,
                    email = email,
                    updatedAt = now
                )
            )

            JvmGoogleFirestoreClient.setDocument(
                FirestorePaths.member(uid),
                FirestoreMappers.memberToMap(member)
            )
            mirrorDepartmentMember(member)

            return finalizeMember(member, localMember.departmentId)
        }

        val invitedMember = loadInvitedMember(uid, email)
        if (invitedMember != null) {
            return invitedMember
        }

        error(MemberProvisioningRules.membershipRequiredMessage())
    }

    private suspend fun loadInvitedMember(uid: String, email: String): Member? {
        val inviteSnapshot = JvmGoogleFirestoreClient.getDocument(FirestorePaths.memberInvite(email))
        if (!inviteSnapshot.exists) return null

        val inviteData = inviteSnapshot.data
        val departmentId = inviteData["departmentId"] as? String ?: return null
        val pendingMemberId = inviteData["pendingMemberId"] as? String
        val now = currentTimeMillis()

        val member = CalhounMembershipNormalizer.normalize(
            Member(
                id = uid,
                departmentId = departmentId,
                email = email,
                firstName = inviteData["firstName"] as? String ?: return null,
                lastName = inviteData["lastName"] as? String ?: return null,
                memberNumber = inviteData["memberNumber"] as? String,
                roles = (inviteData["roles"] as? List<*>)?.mapNotNull { roleName ->
                    (roleName as? String)?.let { runCatching { Role.valueOf(it) }.getOrNull() }
                }?.toSet()?.ifEmpty { setOf(Role.MEMBER) }
                    ?: setOf(Role.MEMBER),
                isActive = inviteData["isActive"] as? Boolean ?: true,
                createdAt = (inviteData["createdAt"] as? Number)?.toLong() ?: now,
                updatedAt = now
            )
        )

        MemberProvisioningRules.validateMemberProfile(member)?.let { message ->
            error(message)
        }

        JvmGoogleFirestoreClient.setDocument(
            FirestorePaths.member(uid),
            FirestoreMappers.memberToMap(member)
        )
        mirrorDepartmentMember(member)

        if (pendingMemberId != null && pendingMemberId != uid) {
            JvmGoogleFirestoreClient.deleteDocument(
                FirestorePaths.departmentMember(departmentId, pendingMemberId)
            )
        }

        JvmGoogleFirestoreClient.deleteDocument(FirestorePaths.memberInvite(email))

        return finalizeMember(member, departmentId)
    }

    private suspend fun finalizeMember(member: Member, rawDepartmentId: String?): Member {
        val normalized = CalhounMembershipNormalizer.normalize(member)
        if (rawDepartmentId != null &&
            CalhounMembershipNormalizer.isLegacyMemberNumberUsedAsDepartmentId(rawDepartmentId)
        ) {
            JvmGoogleFirestoreClient.setDocument(
                FirestorePaths.member(normalized.id),
                FirestoreMappers.memberToMap(normalized)
            )
            mirrorDepartmentMember(normalized)
        }
        return normalized
    }

    private suspend fun mirrorDepartmentMember(member: Member) {
        JvmGoogleFirestoreClient.setDocument(
            FirestorePaths.departmentMember(member.departmentId, member.id),
            FirestoreMappers.memberToMap(member)
        )
    }
}
