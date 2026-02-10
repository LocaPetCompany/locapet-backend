package com.vivire.locapet.app.global.auth.social

import com.vivire.locapet.app.global.exception.SocialLoginFailedException
import com.vivire.locapet.common.config.AuthConfigProperties
import com.vivire.locapet.domain.member.SocialProvider
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class GoogleLoginClient(
    private val authConfigProperties: AuthConfigProperties
) : SocialLoginClient {

    private val restClient = RestClient.create()

    override fun provider(): SocialProvider = SocialProvider.GOOGLE

    override fun getUserInfo(token: String): SocialUserInfo {
        try {
            val url = "${authConfigProperties.social.google.tokenInfoUrl}?id_token=$token"

            val response = restClient.get()
                .uri(url)
                .retrieve()
                .body(Map::class.java)
                ?: throw SocialLoginFailedException("구글 사용자 정보를 가져올 수 없습니다.")

            val audience = response["aud"] as? String
            if (audience != authConfigProperties.social.google.clientId) {
                throw SocialLoginFailedException("구글 클라이언트 ID가 일치하지 않습니다.")
            }

            val sub = response["sub"]?.toString()
                ?: throw SocialLoginFailedException("구글 사용자 ID를 가져올 수 없습니다.")

            return SocialUserInfo(
                socialId = sub,
                email = response["email"] as? String,
                nickname = response["name"] as? String,
                profileImageUrl = response["picture"] as? String
            )
        } catch (e: SocialLoginFailedException) {
            throw e
        } catch (e: Exception) {
            throw SocialLoginFailedException("구글 로그인 처리 중 오류가 발생했습니다: ${e.message}")
        }
    }
}
