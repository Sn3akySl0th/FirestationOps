package com.example.firestationops.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.tasks.await

class FirebaseMemberAccountProvisioner(
    private val context: Context
) {
    suspend fun createSignInAccount(email: String, password: String): Result<String> = runCatching {
        val normalizedEmail = email.trim().lowercase()
        val provisioningAuth = FirebaseAuth.getInstance(provisioningApp())
        try {
            val result = provisioningAuth
                .createUserWithEmailAndPassword(normalizedEmail, password)
                .await()
            result.user?.uid ?: error("Firebase did not return a user id.")
        } catch (error: FirebaseAuthException) {
            when (error.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE" ->
                    error("A Firebase sign-in account already exists for this email.")
                "ERROR_INVALID_EMAIL" ->
                    error("Enter a valid email address.")
                "ERROR_WEAK_PASSWORD" ->
                    error("Initial password must be at least 6 characters.")
                else -> throw error
            }
        } finally {
            provisioningAuth.signOut()
        }
    }

    private fun provisioningApp(): FirebaseApp {
        val existing = FirebaseApp.getApps(context).firstOrNull { it.name == PROVISIONING_APP_NAME }
        if (existing != null) {
            return existing
        }

        val primary = FirebaseApp.getInstance()
        return FirebaseApp.initializeApp(context, primary.options, PROVISIONING_APP_NAME)
            ?: error("Unable to initialize Firebase provisioning app.")
    }

    private companion object {
        const val PROVISIONING_APP_NAME = "MemberProvisioning"
    }
}
