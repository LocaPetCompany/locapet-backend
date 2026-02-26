package com.vivire.locapet.app.api.auth.dto

import jakarta.validation.constraints.NotBlank

data class SocialLoginRequest(
    @field:NotBlank(message = "소셜 토큰은 필수입니다.")
    val socialToken: String
)
