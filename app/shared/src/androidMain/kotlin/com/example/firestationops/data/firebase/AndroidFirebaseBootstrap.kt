package com.example.firestationops.data.firebase

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
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
            installDebugAppCheck(context)
        }
    }

    fun setForegroundActivity(activity: Activity?) {
        foregroundActivity = WeakReference(activity)
    }

    fun foregroundActivity(): Activity? = foregroundActivity.get()

    private fun installDebugAppCheck(context: Context) {
        runCatching {
            val appCheck = FirebaseAppCheck.getInstance()
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
            scheduleDebugSecretLogging(context)
            appCheck.getAppCheckToken(false)
                .addOnSuccessListener {
                    logRegisteredDebugSecret(context)
                    Log.i(TAG, "App Check token exchange succeeded.")
                }
                .addOnFailureListener { error ->
                    logRegisteredDebugSecret(context)
                    scheduleDebugSecretLogging(context)
                    val message = error.message.orEmpty()
                    if (message.contains("firebaseappcheck.googleapis.com", ignoreCase = true)) {
                        Log.e(
                            TAG,
                            "Firebase App Check API is disabled for this project. Enable it at " +
                                "https://console.cloud.google.com/apis/library/firebaseappcheck.googleapis.com?project=firestationops " +
                                "then register the debug secret from logcat in Firebase Console > App Check > Manage debug tokens.",
                            error
                        )
                    } else {
                        Log.w(TAG, "Unable to exchange App Check debug secret for a token", error)
                    }
                }
        }.onFailure { error ->
            Log.w(TAG, "App Check debug provider not installed", error)
        }
    }

    private fun scheduleDebugSecretLogging(context: Context) {
        Handler(Looper.getMainLooper()).postDelayed({ logRegisteredDebugSecret(context) }, 2_000L)
    }

    private fun logRegisteredDebugSecret(context: Context) {
        val firebaseApp = FirebaseApp.getInstance()
        val encodedKey = listOf(firebaseApp.name, firebaseApp.options.applicationId)
            .joinToString("+") { value ->
                Base64.encodeToString(
                    value.toByteArray(Charsets.UTF_8),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                )
            }
        val prefsName = "com.google.firebase.appcheck.debug.store.$encodedKey"
        val debugSecret = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
            ?.trim()
            .orEmpty()
        if (debugSecret.isNotEmpty()) {
            Log.i(
                TAG,
                "App Check debug secret (Firebase Console > App Check > Manage debug tokens): $debugSecret"
            )
        } else {
            Log.i(
                TAG,
                "App Check debug secret not ready yet. Filter logcat for DebugAppCheckProvider."
            )
        }
    }
}
