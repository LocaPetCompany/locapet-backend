package com.vivire.locapet.app.api.auth.dto

data class ReissueResponse(
    val accessToken: String,
    val refreshToken: String
)
