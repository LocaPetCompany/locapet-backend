package com.vivire.locapet.app.global.auth.social

import com.vivire.locapet.app.global.exception.SocialLoginFailedException
import com.vivire.locapet.common.config.AuthConfigProperties
import com.vivire.locapet.domain.member.SocialProvider
import io.jsonwebtoken.Jwts
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@Component
class AppleLoginClient(
    private val authConfigProperties: AuthConfigProperties
) : SocialLoginClient {

    private val restClient = RestClient.create()
    private var cachedKeys: Map<String, PublicKey> = ConcurrentHashMap()
    private var cacheExpiry: Long = 0

    override fun provider(): SocialProvider = SocialProvider.APPLE

    override fun getUserInfo(token: String): SocialUserInfo {
        try {
            val header = parseJwtHeader(token)
            val kid = header["kid"] as? String
                ?: throw SocialLoginFailedException("Apple ID 토큰에 kid가 없습니다.")

            val publicKey = getPublicKey(kid)
                ?: throw SocialLoginFailedException("Apple 공개키를 찾을 수 없습니다.")

            val claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .payload

            val audience = claims.audience
            if (!audience.contains(authConfigProperties.social.apple.clientId)) {
                throw SocialLoginFailedException("Apple 클라이언트 ID가 일치하지 않습니다.")
            }

            return SocialUserInfo(
                socialId = claims.subject,
                email = claims["email"] as? String,
                nickname = null,
                profileImageUrl = null
            )
        } catch (e: SocialLoginFailedException) {
            throw e
        } catch (e: Exception) {
            throw SocialLoginFailedException("Apple 로그인 처리 중 오류가 발생했습니다: ${e.message}")
        }
    }

    private fun parseJwtHeader(token: String): Map<*, *> {
        val headerPart = token.split(".").firstOrNull()
            ?: throw SocialLoginFailedException("잘못된 Apple ID 토큰 형식입니다.")
        val decoded = Base64.getUrlDecoder().decode(headerPart)
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        return mapper.readValue(decoded, Map::class.java)
    }

    private fun getPublicKey(kid: String): PublicKey? {
        if (System.currentTimeMillis() > cacheExpiry || cachedKeys.isEmpty()) {
            refreshApplePublicKeys()
        }
        return cachedKeys[kid]
    }

    @Suppress("UNCHECKED_CAST")
    private fun refreshApplePublicKeys() {
        val response = restClient.get()
            .uri(authConfigProperties.social.apple.publicKeysUrl)
            .retrieve()
            .body(Map::class.java)
            ?: throw SocialLoginFailedException("Apple 공개키를 가져올 수 없습니다.")

        val keys = response["keys"] as? List<Map<String, Any>>
            ?: throw SocialLoginFailedException("Apple 공개키 형식이 올바르지 않습니다.")

        val newKeys = ConcurrentHashMap<String, PublicKey>()
        for (key in keys) {
            val kid = key["kid"] as String
            val n = key["n"] as String
            val e = key["e"] as String

            val nBytes = Base64.getUrlDecoder().decode(n)
            val eBytes = Base64.getUrlDecoder().decode(e)

            val spec = RSAPublicKeySpec(
                BigInteger(1, nBytes),
                BigInteger(1, eBytes)
            )
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(spec)
            newKeys[kid] = publicKey
        }

        cachedKeys = newKeys
        cacheExpiry = System.currentTimeMillis() + 24 * 60 * 60 * 1000 // 24시간
    }
}
