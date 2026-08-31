package com.example.firestationops.data.firebase

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import java.lang.ref.WeakReference

object AndroidFirebaseBootstrap {
    private const val TAG = "FirestationOpsFirebase"
    private var foregroundActivity = WeakReference<Activity>(null)

    fun initialize(context: Context, isDebugBuild: Boolean) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val playServices = GoogleApiAvailability.getInstance()
        val playServicesStatus = playServices.isGooglePlayServicesAvailable(context)
        if (playServicesStatus != ConnectionResult.SUCCESS) {
            Log.e(
                TAG,
                "Google Play services unavailable: ${playServices.getErrorString(playServicesStatus)}"
            )
        } else {
            Log.i(TAG, "Google Play services available")
        }

        if (isDebugBuild) {
            installDebugAppCheck()
        }
    }

    fun setForegroundActivity(activity: Activity?) {
        foregroundActivity = WeakReference(activity)
    }

    fun foregroundActivity(): Activity? = foregroundActivity.get()

    private fun installDebugAppCheck() {
        runCatching {
            val appCheck = FirebaseAppCheck.getInstance()
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            appCheck.getAppCheckToken(false)
                .addOnSuccessListener { token ->
                    Log.i(
                        TAG,
                        "App Check debug token (register in Firebase Console > App Check): ${token.token}"
                    )
                }
                .addOnFailureListener { error ->
                    Log.w(TAG, "Unable to fetch App Check debug token", error)
                }
        }.onFailure { error ->
            Log.w(TAG, "App Check debug provider not installed", error)
        }
    }
}
