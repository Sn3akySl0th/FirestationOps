package com.example.firestationops.data.firebase

import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.auth.AuthSessionRecovery
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

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
                if (isDebugBuild) {
                    forceRecaptchaForDebug()
                }

                withContext(Dispatchers.IO) {
                    runCatching { verifyCredentialsWithRest(normalizedEmail, password) }
                        .onSuccess { restResult ->
                            Log.i(TAG, "rest preflight success uid=${restResult.localId}")
                        }
                        .onFailure { restError ->
                            Log.w(TAG, "rest preflight skipped: ${restError::class.simpleName}: ${restError.message}", restError)
                        }
                }

                val cloudSignedIn = runCatching {
                    auth.signInWithCustomTokenFromCloudFunction(
                        email = normalizedEmail,
                        password = password,
                        timeoutMs = AUTH_TIMEOUT_MS,
                    )
                }.onFailure { cloudError ->
                    Log.w(TAG, "custom token sign-in failed; trying sdk", cloudError)
                }.isSuccess

                if (!cloudSignedIn) {
                    auth.signInWithEmailAndPasswordAwait(
                        email = normalizedEmail,
                        password = password,
                        timeoutMs = AUTH_TIMEOUT_MS,
                    )
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
                    loadCanonicalMember(firebaseUser.uid)
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

    private fun shouldFallbackToOffline(email: String, error: Throwable): Boolean =
        error is FirebaseTaskTimeoutException &&
            database.getMemberByEmail(email) != null

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

    private fun forceRecaptchaForDebug() {
        runCatching {
            val settings = auth.firebaseAuthSettings
            val method = settings.javaClass.getDeclaredMethod(
                "setForceRecaptchaFlowForTesting",
                Boolean::class.javaPrimitiveType,
            )
            method.isAccessible = true
            method.invoke(settings, true)
            Log.i(TAG, "Forced reCAPTCHA flow for debug build")
        }.onFailure { error ->
            Log.w(TAG, "Unable to force reCAPTCHA flow for debug build", error)
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
            message.contains("INVALID_PASSWORD", ignoreCase = true)
        ) {
            return "Incorrect password."
        }
        if (message.contains("permission-denied", ignoreCase = true) ||
            message.contains("UNAUTHENTICATED", ignoreCase = true)
        ) {
            return "Cloud sign-in rejected the email or password. Reset your password in Firebase Console and try again."
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
        const val PROVISION_TIMEOUT_MS = 20_000L
    }
}
