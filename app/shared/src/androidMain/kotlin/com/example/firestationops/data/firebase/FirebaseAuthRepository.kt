package com.example.firestationops.data.firebase

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.bootstrap.DemoDepartmentSeeder
import com.example.firestationops.domain.bootstrap.DepartmentCatalogProfiles
import com.example.firestationops.domain.membership.CalhounMembershipNormalizer
import com.example.firestationops.domain.membership.MemberProvisioningRules
import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.UserState
import com.example.firestationops.domain.repository.AuthRepository
import com.example.firestationops.domain.repository.persistent.PersistentAuthRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val database: FirestationOpsDatabase,
    private val localAuth: PersistentAuthRepository,
    private val firebaseEnabled: Boolean,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
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
        val localState = localAuth.userState.value
        _userState.value = localState
    }

    private fun recoverFirebaseSession() {
        val firebaseUser = auth.currentUser
        if (firebaseUser == null) {
            _userState.value = UserState.Unauthenticated
            return
        }

        val cachedMember = database.getMemberById(firebaseUser.uid)?.let(CalhounMembershipNormalizer::normalize)
        if (cachedMember != null) {
            MemberProvisioningRules.validateMemberProfile(cachedMember)?.let { message ->
                _userState.value = UserState.Error(message)
                return
            }
            database.setSessionUserId(cachedMember.id)
            _userState.value = UserState.Authenticated(cachedMember)
        }
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
        val snapshot = firestore.document(FirestorePaths.member(uid)).get().await()
        if (snapshot.exists()) {
            val rawData = snapshot.data ?: emptyMap()
            val member = FirestoreMappers.memberFromMap(uid, rawData)
                ?: error("Member profile is missing required fields.")
            return finalizeMember(member, rawData["departmentId"] as? String)
        }

        val localMember = database.getMemberByEmail(email)?.let(CalhounMembershipNormalizer::normalize)
        if (!MemberProvisioningRules.canAutoProvisionFromLocal(localMember, email)) {
            error(MemberProvisioningRules.membershipRequiredMessage())
        }

        val now = currentTimeMillis()
        val member = CalhounMembershipNormalizer.normalize(
            localMember!!.copy(
                id = uid,
                email = email,
                updatedAt = now
            )
        )

        firestore.document(FirestorePaths.member(uid))
            .set(FirestoreMappers.memberToMap(member))
            .await()
        mirrorDepartmentMember(member)

        return finalizeMember(member, localMember!!.departmentId)
    }

    private suspend fun finalizeMember(member: Member, rawDepartmentId: String?): Member {
        val normalized = CalhounMembershipNormalizer.normalize(member)
        if (rawDepartmentId != null &&
            CalhounMembershipNormalizer.isLegacyMemberNumberUsedAsDepartmentId(rawDepartmentId)
        ) {
            firestore.document(FirestorePaths.member(normalized.id))
                .set(FirestoreMappers.memberToMap(normalized))
                .await()
            mirrorDepartmentMember(normalized)
        }
        return normalized
    }

    private suspend fun mirrorDepartmentMember(member: Member) {
        firestore.document(FirestorePaths.departmentMember(member.departmentId, member.id))
            .set(FirestoreMappers.memberToMap(member))
            .await()
    }
}
