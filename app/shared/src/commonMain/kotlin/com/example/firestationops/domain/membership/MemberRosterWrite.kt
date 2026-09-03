package com.example.firestationops.domain.membership

import com.example.firestationops.domain.model.Member

data class MemberRosterWrite(
    val member: Member,
    val passwordSetupEmailSent: Boolean? = null
)
