package com.vivire.locapet.app.api.onboarding.dto

data class ProfileCompleteResponse(
    val memberId: Long,
    val accessToken: String,
    val refreshToken: String
)
