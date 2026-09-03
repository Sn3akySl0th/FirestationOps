package com.example.firestationops.domain.auth

object PasswordResetRules {
    private val emailPattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""")

    const val GENERIC_ACCEPTED_MESSAGE =
        "If an account exists for that email, a password reset link will be sent. Check the inbox and spam folder."

    const val UNAVAILABLE_OFFLINE_MESSAGE =
        "Password reset requires cloud sign-in. Ask an administrator or sign in on a device with Firebase configured."

    const val INVITE_EMAIL_SENT_MESSAGE =
        "Member added. They will receive an email to set their own password."

    const val INVITE_EMAIL_FAILED_MESSAGE =
        "Member added, but the password email could not be sent. Use Send password reset."

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun validateEmail(email: String): String? {
        val normalized = normalizeEmail(email)
        if (normalized.isBlank() || !emailPattern.matches(normalized)) {
            return "Enter a valid email address."
        }
        return null
    }
}
