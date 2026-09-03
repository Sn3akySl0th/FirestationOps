package com.example.firestationops.data.firebase

import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal suspend fun FirebaseAuth.signInWithEmailAndPasswordAwait(
    email: String,
    password: String,
    timeoutMs: Long,
): AuthResult = awaitAuthTask(
    timeoutMs = timeoutMs,
    timeoutLabel = "Firebase authentication",
) {
    signInWithEmailAndPassword(email, password)
}

internal suspend fun FirebaseAuth.signInWithCustomTokenAwait(
    customToken: String,
    timeoutMs: Long,
): AuthResult = awaitAuthTask(
    timeoutMs = timeoutMs,
    timeoutLabel = "Firebase custom token sign-in",
) {
    signInWithCustomToken(customToken)
}

private suspend fun awaitAuthTask(
    timeoutMs: Long,
    timeoutLabel: String,
    start: () -> com.google.android.gms.tasks.Task<AuthResult>,
): AuthResult = suspendCancellableCoroutine { continuation ->
    val mainHandler = Handler(Looper.getMainLooper())
    var finished = false
    lateinit var timeoutRunnable: Runnable

    fun complete(block: () -> Unit) {
        if (finished) return
        finished = true
        mainHandler.removeCallbacks(timeoutRunnable)
        block()
    }

    timeoutRunnable = Runnable {
        if (continuation.isActive) {
            complete {
                continuation.resumeWithException(
                    FirebaseTaskTimeoutException("$timeoutLabel timed out after ${timeoutMs}ms")
                )
            }
        }
    }

    mainHandler.postDelayed(timeoutRunnable, timeoutMs)

    continuation.invokeOnCancellation {
        mainHandler.removeCallbacks(timeoutRunnable)
    }

    start().addOnCompleteListener { task ->
        if (!continuation.isActive) {
            complete { }
            return@addOnCompleteListener
        }
        if (task.isSuccessful) {
            val result = task.result
            if (result != null) {
                complete { continuation.resume(result) }
            } else {
                complete {
                    continuation.resumeWithException(
                        IllegalStateException("$timeoutLabel did not return a result.")
                    )
                }
            }
        } else {
            complete {
                continuation.resumeWithException(
                    task.exception ?: IllegalStateException("$timeoutLabel failed.")
                )
            }
        }
    }
}
