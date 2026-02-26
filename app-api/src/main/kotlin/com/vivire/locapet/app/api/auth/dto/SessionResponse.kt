package com.vivire.locapet.app.api.auth.dto

import com.vivire.locapet.domain.member.AccountStatus
import com.vivire.locapet.domain.member.OnboardingStage

data class SessionResponse(
    val memberId: Long,
    val accountStatus: AccountStatus,
    val onboardingStage: OnboardingStage,
    val nickname: String?
)
