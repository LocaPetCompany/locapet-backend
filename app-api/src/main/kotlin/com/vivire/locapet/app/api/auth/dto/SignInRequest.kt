package com.vivire.locapet.app.api.auth.dto

import com.vivire.locapet.domain.member.SocialProvider
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class SignInRequest(
    @field:NotNull(message = "소셜 로그인 제공자는 필수입니다.")
    val provider: SocialProvider,

    @field:NotBlank(message = "소셜 토큰은 필수입니다.")
    val socialToken: String
)
