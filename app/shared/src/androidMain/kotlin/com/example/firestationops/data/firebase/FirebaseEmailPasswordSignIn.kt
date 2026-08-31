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
                    FirebaseTaskTimeoutException("Firebase authentication timed out after ${timeoutMs}ms")
                )
            }
        }
    }

    mainHandler.postDelayed(timeoutRunnable, timeoutMs)

    continuation.invokeOnCancellation {
        mainHandler.removeCallbacks(timeoutRunnable)
    }

    signInWithEmailAndPassword(email, password)
        .addOnCompleteListener { task ->
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
                            IllegalStateException("Firebase sign-in did not return a result.")
                        )
                    }
                }
            } else {
                complete {
                    continuation.resumeWithException(
                        task.exception ?: IllegalStateException("Firebase sign-in failed.")
                    )
                }
            }
        }
}
