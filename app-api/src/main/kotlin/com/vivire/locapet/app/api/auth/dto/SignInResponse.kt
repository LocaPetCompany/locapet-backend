package com.vivire.locapet.app.api.auth.dto

data class SignInResponse(
    val isNewUser: Boolean,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val temporaryToken: String? = null
)
