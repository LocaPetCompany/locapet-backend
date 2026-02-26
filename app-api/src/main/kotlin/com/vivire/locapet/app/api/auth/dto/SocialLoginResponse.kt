package com.vivire.locapet.app.api.auth.dto

enum class SocialLoginResult {
    COMPLETED,
    NEEDS_IDENTITY_VERIFICATION,
    NEEDS_PROFILE
}

data class SocialLoginResponse(
    val result: SocialLoginResult,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val onboardingToken: String? = null,
    val onboardingAccessToken: String? = null
)
