package com.vivire.locapet.app.api.member.dto

data class SignUpResponse(
    val memberId: Long,
    val accessToken: String,
    val refreshToken: String
)
