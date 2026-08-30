package com.example.firestationops.domain.repository.mock

import com.example.firestationops.domain.model.Member
import com.example.firestationops.domain.model.Role
import com.example.firestationops.domain.model.UserState
import com.example.firestationops.domain.repository.AuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockAuthRepository : AuthRepository {
    private val _userState = MutableStateFlow<UserState>(UserState.Unauthenticated)
    override val userState: StateFlow<UserState> = _userState.asStateFlow()

    override suspend fun login(email: String, password: String): Result<Unit> {
        _userState.value = UserState.Loading
        delay(1000) // Simulate network delay
        
        if (email.contains("error")) {
            _userState.value = UserState.Error("Invalid credentials (Mock Error)")
            return Result.failure(Exception("Login failed"))
        }

        val mockMember = Member(
            id = "mock-user-id",
            departmentId = "mock-dept-id",
            email = email,
            firstName = "Mock",
            lastName = "User",
            roles = setOf(Role.ADMIN)
        )
        
        _userState.value = UserState.Authenticated(mockMember)
        return Result.success(Unit)
    }

    override suspend fun logout(): Result<Unit> {
        _userState.value = UserState.Loading
        delay(500)
        _userState.value = UserState.Unauthenticated
        return Result.success(Unit)
    }
}
