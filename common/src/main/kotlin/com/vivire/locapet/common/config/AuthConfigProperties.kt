package com.vivire.locapet.common.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.auth")
data class AuthConfigProperties(
    val jwt: Jwt,
    val social: Social
) {
    data class Jwt(
        val secret: String,
        val accessTokenExpiry: Long,
        val refreshTokenExpiry: Long,
        val temporaryTokenExpiry: Long
    )

    data class Social(
        val kakao: Kakao,
        val naver: Naver,
        val google: Google,
        val apple: Apple
    ) {
        data class Kakao(
            val userInfoUrl: String
        )

        data class Naver(
            val userInfoUrl: String
        )

        data class Google(
            val tokenInfoUrl: String,
            val clientId: String
        )

        data class Apple(
            val publicKeysUrl: String,
            val clientId: String
        )
    }
}
