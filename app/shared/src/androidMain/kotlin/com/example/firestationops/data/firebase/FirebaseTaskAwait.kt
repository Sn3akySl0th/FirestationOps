package com.example.firestationops.data.firebase

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class FirebaseTaskTimeoutException(message: String) : TimeoutException(message)

internal fun <T> Task<T>.awaitOrTimeout(timeoutMs: Long, label: String): T {
    return try {
        Tasks.await(this, timeoutMs, TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
        throw FirebaseTaskTimeoutException("$label timed out after ${timeoutMs}ms")
    } catch (execution: ExecutionException) {
        throw execution.cause ?: execution
    }
}
