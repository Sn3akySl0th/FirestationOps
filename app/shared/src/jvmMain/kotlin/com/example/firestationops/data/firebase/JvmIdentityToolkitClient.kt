package com.example.firestationops.data.firebase

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class JvmPasswordResetRequest(
    val requestType: String = "PASSWORD_RESET",
    val email: String,
)

@Serializable
private data class JvmErrorResponse(
    val error: JvmErrorDetail? = null,
)

@Serializable
private data class JvmErrorDetail(
    val message: String? = null,
)

internal object JvmIdentityToolkitClient {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun sendPasswordResetEmail(
        apiKey: String,
        email: String,
        timeoutMs: Int = 15_000,
    ): Result<Unit> {
        val connection = (URL(
            "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$apiKey"
        ).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        return runCatching {
            val payload = json.encodeToString(
                JvmPasswordResetRequest.serializer(),
                JvmPasswordResetRequest(
                    requestType = "PASSWORD_RESET",
                    email = email,
                )
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
                    json.decodeFromString(JvmErrorResponse.serializer(), body).error?.message
                }.getOrNull() ?: body.ifBlank { "HTTP $responseCode" }
                error(message)
            }
        }
    }
}
