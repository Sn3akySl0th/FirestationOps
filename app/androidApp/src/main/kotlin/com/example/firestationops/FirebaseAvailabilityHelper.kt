package com.example.firestationops

import android.content.Context
import com.example.firestationops.data.firebase.FirebaseAvailability

internal object FirebaseAvailabilityHelper {
    fun isConfigured(context: Context): Boolean = FirebaseAvailability.isConfigured(context)

    fun googleApiKey(context: Context): String? {
        val resourceId = context.resources.getIdentifier("google_api_key", "string", context.packageName)
        if (resourceId == 0) return null
        return context.getString(resourceId)
    }
}
