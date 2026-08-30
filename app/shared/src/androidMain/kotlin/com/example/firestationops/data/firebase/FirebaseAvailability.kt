package com.example.firestationops.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp

object FirebaseAvailability {
    fun isConfigured(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)
}
