package com.example.firestationops.domain.repository

import com.example.firestationops.domain.model.UserState
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val userState: StateFlow<UserState>
    
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun logout(): Result<Unit>
}
