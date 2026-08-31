package com.example.firestationops.domain.membership

import com.example.firestationops.domain.model.Role

data class MemberRosterInput(
    val email: String,
    val firstName: String,
    val lastName: String,
    val memberNumber: String? = null,
    val roles: Set<Role> = setOf(Role.MEMBER),
    val isActive: Boolean = true,
    val initialPassword: String? = null
)
