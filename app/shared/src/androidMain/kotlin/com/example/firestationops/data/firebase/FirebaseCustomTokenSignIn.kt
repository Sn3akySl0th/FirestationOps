package com.example.firestationops.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun FirebaseAuth.signInWithCustomTokenFromCloudFunction(
    email: String,
    password: String,
    timeoutMs: Long,
): Unit {
    val customToken = withContext(Dispatchers.IO) {
        val result = FirebaseFunctions.getInstance()
            .getHttpsCallable("issueCustomToken")
            .call(
                mapOf(
                    "email" to email,
                    "password" to password,
                )
            )
            .awaitOrTimeout(timeoutMs, "Custom token sign-in")

        (result.data as? Map<*, *>)?.get("customToken") as? String
            ?: error("Cloud Function did not return a custom token.")
    }

    // Auth session establishment must not use Tasks.await on a blocked Main looper.
    withContext(Dispatchers.Main) {
        signInWithCustomTokenAwait(customToken, timeoutMs)
    }
}
