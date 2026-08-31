package com.example.firestationops.data.firebase

import com.google.firebase.auth.FirebaseAuth

internal object JvmCloudAuth {
    fun isSignedIn(): Boolean = FirebaseAuth.getInstance().currentUser != null
}
