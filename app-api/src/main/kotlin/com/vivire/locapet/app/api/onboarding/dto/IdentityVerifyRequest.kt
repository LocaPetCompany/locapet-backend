package com.vivire.locapet.app.api.onboarding.dto

import jakarta.validation.constraints.NotBlank

data class IdentityVerifyRequest(
    @field:NotBlank(message = "온보딩 토큰은 필수입니다.")
    val onboardingToken: String,

    @field:NotBlank(message = "트랜잭션 ID는 필수입니다.")
    val transactionId: String
)
