package com.example.firestationops.data.firebase

import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.auth.AuthSessionRecovery
import com.example.firestationops.domain.auth.PasswordResetRules
import com.example.firestationops.domain.bootstrap.DemoDepartmentSeeder
import com.example.firestationops.domain.bootstrap.DepartmentCatalogProfiles
import com.example.firestationops.domain.membership.MemberProvisioningRules
import com.example.firestationops.domain.model.Member
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
            val cachedMember = database.getMemberById(firebaseUser.uid)
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
            val member = loadCanonicalMember(firebaseUser.uid)
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

    override suspend fun requestPasswordReset(email: String): Result<Unit> {
        PasswordResetRules.validateEmail(email)?.let {
            return Result.failure(IllegalArgumentException(it))
        }
        if (!firebaseEnabled) {
            return Result.failure(IllegalStateException(PasswordResetRules.UNAVAILABLE_OFFLINE_MESSAGE))
        }
        val apiKey = DesktopFirebaseConfig.load()?.apiKey?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException(PasswordResetRules.UNAVAILABLE_OFFLINE_MESSAGE))
        val normalized = PasswordResetRules.normalizeEmail(email)
        return JvmIdentityToolkitClient.sendPasswordResetEmail(apiKey, normalized)
            .recover { error ->
                val message = error.message.orEmpty()
                if (
                    message.contains("EMAIL_NOT_FOUND", ignoreCase = true) ||
                    message.contains("USER_NOT_FOUND", ignoreCase = true)
                ) {
                    return@recover
                }
                throw error
            }
    }

    private suspend fun loadCanonicalMember(uid: String): Member {
        val snapshot = JvmGoogleFirestoreClient.getDocument(FirestorePaths.member(uid))
        if (!snapshot.exists) {
            error(MemberProvisioningRules.membershipRequiredMessage())
        }
        return FirestoreMappers.memberFromMap(uid, snapshot.data)
            ?: error("Member profile is missing required fields.")
    }
}
