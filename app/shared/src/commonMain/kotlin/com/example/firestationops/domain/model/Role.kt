package com.example.firestationops.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    MEMBER,
    APPARATUS_OFFICER,
    OFFICER,
    ADMIN
}
