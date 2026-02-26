package com.vivire.locapet.app.api.onboarding.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ProfileCompleteRequest(
    @field:NotBlank(message = "온보딩 액세스 토큰은 필수입니다.")
    val onboardingAccessToken: String,

    @field:NotBlank(message = "닉네임은 필수입니다.")
    @field:Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
    val nickname: String,

    val termsOfServiceAgreed: Boolean,
    val privacyPolicyAgreed: Boolean,
    val marketingConsent: Boolean = false
)
