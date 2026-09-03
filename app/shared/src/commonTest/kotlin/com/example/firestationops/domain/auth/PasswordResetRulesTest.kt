package com.example.firestationops.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PasswordResetRulesTest {
    @Test
    fun validateEmail_rejectsBlankAndMalformed() {
        assertEquals("Enter a valid email address.", PasswordResetRules.validateEmail(""))
        assertEquals("Enter a valid email address.", PasswordResetRules.validateEmail("not-an-email"))
        assertNull(PasswordResetRules.validateEmail(" member@example.com "))
    }

    @Test
    fun normalizeEmail_lowercasesAndTrims() {
        assertEquals("member@example.com", PasswordResetRules.normalizeEmail("  Member@Example.com "))
    }
}
