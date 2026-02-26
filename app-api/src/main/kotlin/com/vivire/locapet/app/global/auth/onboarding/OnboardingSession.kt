package com.vivire.locapet.app.global.auth.onboarding

import com.vivire.locapet.domain.member.SocialProvider

data class OnboardingSession(
    val socialId: String,
    val provider: SocialProvider,
    val email: String?,
    val profileImageUrl: String?
)
