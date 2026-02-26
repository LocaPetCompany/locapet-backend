package com.vivire.locapet.app.api.onboarding.dto

enum class IdentityVerifyResult {
    COMPLETED,
    NEEDS_PROFILE
}

data class IdentityVerifyResponse(
    val result: IdentityVerifyResult,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val onboardingAccessToken: String? = null
)
