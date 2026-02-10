package com.vivire.locapet.app.global.auth

import com.vivire.locapet.common.config.AuthConfigProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class RefreshTokenService(
    private val redisTemplate: StringRedisTemplate,
    private val authConfigProperties: AuthConfigProperties
) {
    companion object {
        private const val REFRESH_TOKEN_PREFIX = "refresh_token:"
    }

    fun save(memberId: Long, refreshToken: String) {
        val key = REFRESH_TOKEN_PREFIX + memberId
        redisTemplate.opsForValue().set(
            key,
            refreshToken,
            authConfigProperties.jwt.refreshTokenExpiry,
            TimeUnit.MILLISECONDS
        )
    }

    fun find(memberId: Long): String? {
        val key = REFRESH_TOKEN_PREFIX + memberId
        return redisTemplate.opsForValue().get(key)
    }

    fun delete(memberId: Long) {
        val key = REFRESH_TOKEN_PREFIX + memberId
        redisTemplate.delete(key)
    }

    fun matches(memberId: Long, refreshToken: String): Boolean {
        val stored = find(memberId) ?: return false
        return stored == refreshToken
    }
}
