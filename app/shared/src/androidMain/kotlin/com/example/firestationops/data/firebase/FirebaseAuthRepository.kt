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
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

class FirebaseAuthRepository(
    private val database: FirestationOpsDatabase,
    private val localAuth: PersistentAuthRepository,
    private val firebaseEnabled: Boolean,
    private val googleApiKey: String?,
    private val isDebugBuild: Boolean,
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

        return withContext(Dispatchers.Main) {
            var stage = LoginStage.AUTH
            try {
                Log.i(TAG, "login start foregroundActivity=${AndroidFirebaseBootstrap.foregroundActivity() != null}")

                // Prove email/password against Identity Toolkit first. This bypasses Play Integrity
                // and fails fast on bad credentials.
                val restResult = withContext(Dispatchers.IO) {
                    verifyCredentialsWithRest(normalizedEmail, password)
                }
                Log.i(TAG, "rest credential check success uid=${restResult.localId}")

                val customTokenError = withContext(Dispatchers.IO) {
                    runCatching {
                        auth.signInWithCustomTokenFromCloudFunction(
                            email = normalizedEmail,
                            password = password,
                            timeoutMs = AUTH_TIMEOUT_MS,
                        )
                    }.exceptionOrNull()
                }

                if (customTokenError != null) {
                    Log.w(TAG, "custom token sign-in failed; trying sdk email/password", customTokenError)
                    // Do not force reCAPTCHA — that hangs sideloaded debug builds.
                    runCatching {
                        auth.signInWithEmailAndPasswordAwait(
                            email = normalizedEmail,
                            password = password,
                            timeoutMs = AUTH_TIMEOUT_MS,
                        )
                    }.getOrElse { sdkError ->
                        Log.e(TAG, "sdk email/password sign-in failed", sdkError)
                        throw IllegalStateException(
                            buildString {
                                append("Cloud sign-in failed after verifying your password. ")
                                append(
                                    customTokenError.message?.takeIf { it.isNotBlank() }
                                        ?: sdkError.message
                                        ?: "Try again or use offline sign-in."
                                )
                            },
                            sdkError,
                        )
                    }
                }

                Log.i(TAG, "firebase auth complete uid=${auth.currentUser?.uid}")
                val firebaseUser = auth.currentUser ?: error("Firebase sign-in did not return a user.")
                withContext(Dispatchers.IO) {
                    runCatching { auth.syncMemberClaims(AUTH_TIMEOUT_MS) }
                        .onSuccess { Log.i(TAG, "member claims synced") }
                        .onFailure { claimsError ->
                            Log.w(TAG, "member claims sync skipped", claimsError)
                        }
                }
                stage = LoginStage.PROVISION
                val member = withContext(Dispatchers.IO) {
                    loadCanonicalMemberOrCache(firebaseUser.uid, normalizedEmail)
                }
                Log.i(TAG, "canonical membership loaded uid=${member.id}")
                MemberProvisioningRules.validateMemberProfile(member)?.let { message ->
                    error(message)
                }
                database.upsertCanonicalMember(member)
                database.setSessionUserId(member.id)
                if (DepartmentCatalogProfiles.profileFor(member.departmentId) != null) {
                    DemoDepartmentSeeder.ensureDemoData(database, member.departmentId)
                }
                _userState.value = UserState.Authenticated(member)
                Result.success(Unit)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.e(TAG, "login failed during $stage", error)
                if (auth.currentUser != null && error is FirebaseTaskTimeoutException) {
                    auth.signOut()
                }
                if (stage == LoginStage.AUTH && shouldFallbackToOffline(normalizedEmail, error)) {
                    Log.w(TAG, "falling back to offline login")
                    val offlineResult = localAuth.login(normalizedEmail, password)
                    if (offlineResult.isSuccess) {
                        mirrorLocalAuthState()
                        return@withContext offlineResult
                    }
                }
                _userState.value = UserState.Error(loginErrorMessage(error, stage))
                Result.failure(error)
            }
        }
    }

    override suspend fun loginOffline(email: String, password: String): Result<Unit> {
        val result = localAuth.login(email, password)
        mirrorLocalAuthState()
        return result
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
        val normalized = PasswordResetRules.normalizeEmail(email)
        // Identity Toolkit REST on IO — never block Main with Tasks.await.
        return withContext(Dispatchers.IO) {
            try {
                withTimeout(PASSWORD_RESET_TIMEOUT_MS) {
                    val apiKey = googleApiKey?.takeIf { it.isNotBlank() }
                        ?: error(PasswordResetRules.UNAVAILABLE_OFFLINE_MESSAGE)
                    FirebaseIdentityRestClient.sendPasswordResetEmail(
                        apiKey = apiKey,
                        email = normalized,
                        timeoutMs = PASSWORD_RESET_TIMEOUT_MS.toInt(),
                    ).recover { error ->
                        val message = error.message.orEmpty()
                        if (
                            message.contains("EMAIL_NOT_FOUND", ignoreCase = true) ||
                            message.contains("USER_NOT_FOUND", ignoreCase = true)
                        ) {
                            Unit
                        } else {
                            throw error
                        }
                    }
                }
            } catch (error: TimeoutCancellationException) {
                Result.failure(
                    IllegalStateException("Password reset timed out. Check your connection and try again.")
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }
    }

    private suspend fun loadCanonicalMember(uid: String): Member {
        val snapshot = firestore.document(FirestorePaths.member(uid))
            .get(Source.SERVER)
            .awaitOrTimeout(PROVISION_TIMEOUT_MS, "Member profile lookup")
        if (!snapshot.exists()) {
            error(MemberProvisioningRules.membershipRequiredMessage())
        }
        return FirestoreMappers.memberFromMap(uid, snapshot.data ?: emptyMap())
            ?: error("Member profile is missing required fields.")
    }

    private suspend fun loadCanonicalMemberOrCache(uid: String, email: String): Member {
        return runCatching { loadCanonicalMember(uid) }.getOrElse { serverError ->
            Log.w(TAG, "server member lookup failed; trying local cache", serverError)
            database.getMemberById(uid)
                ?: database.getMemberByEmail(email)?.copy(id = uid)
                ?: throw serverError
        }
    }

    private fun shouldFallbackToOffline(email: String, error: Throwable): Boolean {
        if (database.getMemberByEmail(email) == null) return false
        return error is FirebaseTaskTimeoutException ||
            error.message.orEmpty().contains("Cloud sign-in failed after verifying", ignoreCase = true)
    }

    private fun verifyCredentialsWithRest(email: String, password: String): RestSignInResult {
        val apiKey = googleApiKey?.takeIf { it.isNotBlank() }
            ?: error("Firebase API key is missing from google-services.json.")
        return FirebaseIdentityRestClient.signInWithPassword(
            apiKey = apiKey,
            email = email,
            password = password,
        ).getOrElse { error ->
            val message = error.message.orEmpty()
            error(
                when {
                    message.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                        message.contains("INVALID_PASSWORD", ignoreCase = true) ->
                        "Incorrect password."
                    message.contains("EMAIL_NOT_FOUND", ignoreCase = true) ->
                        "No Firebase account exists for this email."
                    message.contains("USER_DISABLED", ignoreCase = true) ->
                        "This Firebase account is disabled."
                    message.isBlank() ->
                        "Firebase REST sign-in failed. Check internet access on this device."
                    else -> "Firebase REST sign-in failed: $message"
                }
            )
        }
    }

    private fun loginErrorMessage(error: Throwable, stage: LoginStage): String {
        if (error is FirebaseTaskTimeoutException) {
            return when (stage) {
                LoginStage.AUTH ->
                    "Firebase sign-in timed out. Try again, use offline sign-in below, or reset your password in Firebase Console."
                LoginStage.PROVISION ->
                    "Signed in, but loading your member profile timed out. Try offline sign-in below."
            }
        }
        val message = error.message.orEmpty()
        if (message.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
            message.contains("INVALID_PASSWORD", ignoreCase = true) ||
            message.contains("Incorrect password", ignoreCase = true)
        ) {
            return "Incorrect password."
        }
        if (message.contains("permission-denied", ignoreCase = true) ||
            message.contains("UNAUTHENTICATED", ignoreCase = true) ||
            message.contains("Active department membership required", ignoreCase = true)
        ) {
            return "Cloud sign-in rejected this account. Confirm the member profile is active in Firestore."
        }
        return message.ifBlank { "Sign-in failed." }
    }

    private enum class LoginStage {
        AUTH,
        PROVISION,
    }

    private companion object {
        const val TAG = "FirestationOpsAuth"
        const val AUTH_TIMEOUT_MS = 20_000L
        const val PASSWORD_RESET_TIMEOUT_MS = 15_000L
        const val PROVISION_TIMEOUT_MS = 20_000L
    }
}
