package com.example.firestationops.data.firebase

import com.example.firestationops.currentTimeMillis
import com.example.firestationops.db.FirestationOpsDatabase
import com.example.firestationops.domain.bootstrap.DemoDepartmentSeeder
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

        val cachedMember = database.getMemberById(firebaseUser.uid)
        if (cachedMember != null) {
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
            database.insertMember(member)
            database.setSessionUserId(member.id)
            DemoDepartmentSeeder.ensureDemoData(database, member.departmentId)
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
            val data = snapshot.data ?: emptyMap()
            return FirestoreMappers.memberFromMap(uid, data)
                ?: error("Member profile is missing required fields.")
        }

        val localMember = database.getMemberByEmail(email)
        val now = currentTimeMillis()
        val member = if (localMember != null) {
            localMember.copy(
                id = uid,
                email = email,
                updatedAt = now
            )
        } else {
            Member(
                id = uid,
                departmentId = "mock-dept-id",
                email = email,
                firstName = "Member",
                lastName = "User",
                createdAt = now,
                updatedAt = now
            )
        }

        firestore.document(FirestorePaths.member(uid))
            .set(FirestoreMappers.memberToMap(member))
            .await()

        return member
    }
}
