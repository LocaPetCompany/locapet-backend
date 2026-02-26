package com.vivire.locapet.app.global.auth.identity

import java.time.LocalDate

interface IdentityVerificationProvider {
    fun verify(transactionId: String): IdentityVerificationResult
}

data class IdentityVerificationResult(
    val ci: String,
    val phone: String,
    val name: String,
    val birthDate: LocalDate
)
