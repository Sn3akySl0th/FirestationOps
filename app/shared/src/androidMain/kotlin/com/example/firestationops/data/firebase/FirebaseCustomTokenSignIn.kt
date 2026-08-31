package com.example.firestationops.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

internal suspend fun FirebaseAuth.signInWithCustomTokenFromCloudFunction(
    email: String,
    password: String,
    timeoutMs: Long,
): Unit {
    val result = FirebaseFunctions.getInstance()
        .getHttpsCallable("issueCustomToken")
        .call(
            mapOf(
                "email" to email,
                "password" to password,
            )
        )
        .awaitOrTimeout(timeoutMs, "Custom token sign-in")

    val customToken = (result.data as? Map<*, *>)?.get("customToken") as? String
        ?: error("Cloud Function did not return a custom token.")

    signInWithCustomToken(customToken)
        .awaitOrTimeout(timeoutMs, "Firebase custom token sign-in")
}
