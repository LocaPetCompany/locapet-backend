package com.vivire.locapet.app.global.auth.social

import com.vivire.locapet.app.global.exception.SocialLoginFailedException
import com.vivire.locapet.domain.member.SocialProvider
import org.springframework.stereotype.Component

@Component
class SocialLoginClientResolver(
    clients: List<SocialLoginClient>
) {
    private val clientMap: Map<SocialProvider, SocialLoginClient> =
        clients.associateBy { it.provider() }

    fun resolve(provider: SocialProvider): SocialLoginClient {
        return clientMap[provider]
            ?: throw SocialLoginFailedException("지원하지 않는 소셜 로그인 제공자입니다: $provider")
    }
}
