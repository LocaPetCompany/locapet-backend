package com.vivire.locapet.app.global.auth.onboarding

import com.vivire.locapet.common.config.AuthConfigProperties
import com.vivire.locapet.domain.member.SocialProvider
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class OnboardingSessionService(
    private val redisTemplate: StringRedisTemplate,
    private val authConfigProperties: AuthConfigProperties
) {
    companion object {
        private const val KEY_PREFIX = "onboarding:"
    }

    fun create(session: OnboardingSession): String {
        val token = UUID.randomUUID().toString()
        val key = KEY_PREFIX + token
        val value = "${session.socialId}|${session.provider.name}|${session.email ?: ""}|${session.profileImageUrl ?: ""}"
        redisTemplate.opsForValue().set(
            key,
            value,
            authConfigProperties.identityVerification.onboardingSessionTtl,
            TimeUnit.MILLISECONDS
        )
        return token
    }

    fun consume(token: String): OnboardingSession? {
        val key = KEY_PREFIX + token
        val value = redisTemplate.opsForValue().getAndDelete(key) ?: return null
        val parts = value.split("|", limit = 4)
        if (parts.size < 4) return null
        return OnboardingSession(
            socialId = parts[0],
            provider = SocialProvider.valueOf(parts[1]),
            email = parts[2].ifEmpty { null },
            profileImageUrl = parts[3].ifEmpty { null }
        )
    }
}
