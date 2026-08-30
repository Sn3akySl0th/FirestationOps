package com.example.firestationops.domain.model

sealed interface UserState {
    data object Unauthenticated : UserState
    data object Loading : UserState
    data class Authenticated(val member: Member) : UserState
    data class Error(val message: String) : UserState
}
