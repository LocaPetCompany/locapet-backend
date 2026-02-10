package com.vivire.locapet.app.global.auth

import com.vivire.locapet.app.global.exception.ExpiredTokenException
import com.vivire.locapet.app.global.exception.InvalidTokenException
import com.vivire.locapet.common.config.AuthConfigProperties
import com.vivire.locapet.domain.member.SocialProvider
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val authConfigProperties: AuthConfigProperties
) {
    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(authConfigProperties.jwt.secret.toByteArray())
    }

    fun createAccessToken(memberId: Long, role: String): String {
        return createToken(
            claims = mapOf("role" to role, "type" to "ACCESS"),
            subject = memberId.toString(),
            expiry = authConfigProperties.jwt.accessTokenExpiry
        )
    }

    fun createRefreshToken(memberId: Long): String {
        return createToken(
            claims = mapOf("type" to "REFRESH"),
            subject = memberId.toString(),
            expiry = authConfigProperties.jwt.refreshTokenExpiry
        )
    }

    fun createTemporaryToken(
        socialId: String,
        provider: SocialProvider,
        email: String?,
        profileImageUrl: String?
    ): String {
        val claims = mutableMapOf<String, Any>(
            "provider" to provider.name,
            "type" to "TEMPORARY"
        )
        email?.let { claims["email"] = it }
        profileImageUrl?.let { claims["profileImageUrl"] = it }

        return createToken(
            claims = claims,
            subject = socialId,
            expiry = authConfigProperties.jwt.temporaryTokenExpiry
        )
    }

    fun validateAndGetClaims(token: String): Claims {
        try {
            return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .payload
        } catch (e: ExpiredJwtException) {
            throw ExpiredTokenException()
        } catch (e: Exception) {
            throw InvalidTokenException()
        }
    }

    fun getMemberIdFromToken(token: String): Long {
        val claims = validateAndGetClaims(token)
        return claims.subject.toLong()
    }

    fun getTokenType(token: String): String {
        val claims = validateAndGetClaims(token)
        return claims["type"] as String
    }

    private fun createToken(claims: Map<String, Any>, subject: String, expiry: Long): String {
        val now = Date()
        val expiryDate = Date(now.time + expiry)

        return Jwts.builder()
            .claims(claims)
            .subject(subject)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact()
    }
}
