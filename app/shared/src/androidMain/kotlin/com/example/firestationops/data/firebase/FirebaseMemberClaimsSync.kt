package com.example.firestationops.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

internal suspend fun FirebaseAuth.syncMemberClaims(timeoutMs: Long) {
    FirebaseFunctions.getInstance()
        .getHttpsCallable("syncMemberClaims")
        .call(emptyMap<String, Any>())
        .awaitOrTimeout(timeoutMs, "Sync member claims")

    currentUser?.getIdToken(true)?.awaitOrTimeout(timeoutMs, "Refresh ID token")
}
