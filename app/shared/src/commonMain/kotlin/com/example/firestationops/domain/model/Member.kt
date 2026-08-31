package com.example.firestationops.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Member(
    val id: String,
    val departmentId: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val memberNumber: String? = null,
    val roles: Set<Role> = setOf(Role.MEMBER),
    val isActive: Boolean = true,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    val fullName: String get() = "$firstName $lastName"
    
    fun hasRole(role: Role): Boolean = roles.contains(role) || roles.contains(Role.ADMIN)
}
