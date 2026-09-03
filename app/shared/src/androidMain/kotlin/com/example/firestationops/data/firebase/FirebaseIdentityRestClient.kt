package com.example.firestationops.data.firebase

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RestSignInRequest(
    val email: String,
    val password: String,
    @SerialName("returnSecureToken") val returnSecureToken: Boolean = true,
)

@Serializable
private data class RestPasswordResetRequest(
    val requestType: String = "PASSWORD_RESET",
    val email: String,
)

@Serializable
private data class RestSignInResponse(
    @SerialName("localId") val localId: String? = null,
    val email: String? = null,
    @SerialName("idToken") val idToken: String? = null,
    @SerialName("refreshToken") val refreshToken: String? = null,
)

@Serializable
private data class RestErrorResponse(
    val error: RestErrorDetail? = null,
)

@Serializable
private data class RestErrorDetail(
    val code: Int? = null,
    val message: String? = null,
)

data class RestSignInResult(
    val localId: String,
    val email: String,
    val idToken: String,
    val refreshToken: String = "",
)

internal object FirebaseIdentityRestClient {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun signInWithPassword(
        apiKey: String,
        email: String,
        password: String,
        timeoutMs: Int = 15_000,
    ): Result<RestSignInResult> {
        var connection: HttpURLConnection? = null
        return runCatching {
            connection = (URL(
                "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey"
            ).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val active = checkNotNull(connection)
            val payload = json.encodeToString(
                RestSignInRequest.serializer(),
                RestSignInRequest(email = email, password = password)
            )
            active.outputStream.use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
            }

            val responseCode = active.responseCode
            val body = (if (responseCode in 200..299) active.inputStream else active.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()

            if (responseCode !in 200..299) {
                val message = runCatching {
                    json.decodeFromString(RestErrorResponse.serializer(), body)
                        .error
                        ?.message
                }.getOrNull() ?: body.ifBlank { "HTTP $responseCode" }
                error(message)
            }

            val response = json.decodeFromString(RestSignInResponse.serializer(), body)
            val localId = response.localId?.takeIf { it.isNotBlank() }
                ?: error("Firebase REST sign-in returned no localId.")
            RestSignInResult(
                localId = localId,
                email = response.email?.takeIf { it.isNotBlank() } ?: email,
                idToken = response.idToken.orEmpty(),
                refreshToken = response.refreshToken.orEmpty(),
            )
        }.mapFailure { error ->
            if (error.message.isNullOrBlank()) {
                Exception("${error::class.simpleName}: unable to reach Firebase Identity Toolkit", error)
            } else {
                error
            }
        }.also {
            connection?.disconnect()
        }
    }

    fun sendPasswordResetEmail(
        apiKey: String,
        email: String,
        timeoutMs: Int = 15_000,
    ): Result<Unit> {
        var connection: HttpURLConnection? = null
        return runCatching {
            connection = (URL(
                "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$apiKey"
            ).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            val active = checkNotNull(connection)
            val payload = json.encodeToString(
                RestPasswordResetRequest.serializer(),
                RestPasswordResetRequest(
                    requestType = "PASSWORD_RESET",
                    email = email,
                )
            )
            active.outputStream.use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
            }
            val responseCode = active.responseCode
            val body = (if (responseCode in 200..299) active.inputStream else active.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (responseCode !in 200..299) {
                val message = runCatching {
                    json.decodeFromString(RestErrorResponse.serializer(), body)
                        .error
                        ?.message
                }.getOrNull() ?: body.ifBlank { "HTTP $responseCode" }
                error(message)
            }
            Unit
        }.mapFailure { error ->
            if (error.message.isNullOrBlank()) {
                Exception("${error::class.simpleName}: unable to reach Firebase Identity Toolkit", error)
            } else {
                error
            }
        }.also {
            connection?.disconnect()
        }
    }
}

private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
    exceptionOrNull()?.let { Result.failure(transform(it)) } ?: this
