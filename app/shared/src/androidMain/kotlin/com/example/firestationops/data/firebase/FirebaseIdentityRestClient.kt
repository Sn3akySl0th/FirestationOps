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
private data class RestSignInResponse(
    @SerialName("localId") val localId: String,
    val email: String,
    @SerialName("idToken") val idToken: String,
    @SerialName("refreshToken") val refreshToken: String,
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
    val refreshToken: String,
)

internal object FirebaseIdentityRestClient {
    private val json = Json { ignoreUnknownKeys = true }

    fun signInWithPassword(
        apiKey: String,
        email: String,
        password: String,
        timeoutMs: Int = 15_000,
    ): Result<RestSignInResult> {
        val connection = (URL(
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey"
        ).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }

        return runCatching {
            val payload = json.encodeToString(
                RestSignInRequest.serializer(),
                RestSignInRequest(email = email, password = password)
            )
            connection.outputStream.use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
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
            RestSignInResult(
                localId = response.localId,
                email = response.email,
                idToken = response.idToken,
                refreshToken = response.refreshToken,
            )
        }.mapFailure { error ->
            if (error.message.isNullOrBlank()) {
                Exception("${error::class.simpleName}: unable to reach Firebase Identity Toolkit", error)
            } else {
                error
            }
        }
    }
}

private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
    exceptionOrNull()?.let { Result.failure(transform(it)) } ?: this
