package com.vivire.locapet.app.global.auth.social

import com.vivire.locapet.domain.member.SocialProvider

interface SocialLoginClient {
    fun provider(): SocialProvider
    fun getUserInfo(token: String): SocialUserInfo
}

data class SocialUserInfo(
    val socialId: String,
    val email: String?,
    val nickname: String?,
    val profileImageUrl: String?
)
