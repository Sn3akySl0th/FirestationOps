package com.example.firestationops.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.firestationops.domain.repository.AuthRepository
import com.example.firestationops.domain.auth.PasswordResetRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    val userState = authRepository.userState

    private val _passwordResetMessage = MutableStateFlow<String?>(null)
    val passwordResetMessage: StateFlow<String?> = _passwordResetMessage.asStateFlow()

    private val _isResettingPassword = MutableStateFlow(false)
    val isResettingPassword: StateFlow<Boolean> = _isResettingPassword.asStateFlow()

    private var authJob: Job? = null
    private var passwordResetJob: Job? = null

    fun onEmailChange(value: String) {
        _email.value = value
    }

    fun onPasswordChange(value: String) {
        _password.value = value
    }

    fun login() {
        authJob?.cancel()
        authJob = viewModelScope.launch {
            authRepository.login(_email.value, _password.value)
        }
    }

    fun loginOffline() {
        authJob?.cancel()
        passwordResetJob?.cancel()
        _isResettingPassword.value = false
        authJob = viewModelScope.launch {
            authRepository.loginOffline(_email.value, _password.value)
        }
    }

    fun logout() {
        authJob?.cancel()
        passwordResetJob?.cancel()
        _isResettingPassword.value = false
        authJob = viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun requestPasswordReset(emailOverride: String? = null) {
        passwordResetJob?.cancel()
        passwordResetJob = viewModelScope.launch {
            _isResettingPassword.value = true
            _passwordResetMessage.value = null
            try {
                val result = authRepository.requestPasswordReset(emailOverride ?: _email.value)
                _passwordResetMessage.value = if (result.isSuccess) {
                    PasswordResetRules.GENERIC_ACCEPTED_MESSAGE
                } else {
                    result.exceptionOrNull()?.message ?: "Unable to send a password reset email."
                }
            } catch (error: Throwable) {
                _passwordResetMessage.value =
                    error.message?.takeIf { it.isNotBlank() }
                        ?: "Unable to send a password reset email."
            } finally {
                _isResettingPassword.value = false
            }
        }
    }

    fun clearPasswordResetMessage() {
        _passwordResetMessage.value = null
    }
}
