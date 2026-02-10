package com.vivire.locapet.app.global.auth.social

import com.vivire.locapet.app.global.exception.SocialLoginFailedException
import com.vivire.locapet.common.config.AuthConfigProperties
import com.vivire.locapet.domain.member.SocialProvider
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class NaverLoginClient(
    private val authConfigProperties: AuthConfigProperties
) : SocialLoginClient {

    private val restClient = RestClient.create()

    override fun provider(): SocialProvider = SocialProvider.NAVER

    override fun getUserInfo(token: String): SocialUserInfo {
        try {
            val response = restClient.get()
                .uri(authConfigProperties.social.naver.userInfoUrl)
                .header("Authorization", "Bearer $token")
                .retrieve()
                .body(Map::class.java)
                ?: throw SocialLoginFailedException("네이버 사용자 정보를 가져올 수 없습니다.")

            @Suppress("UNCHECKED_CAST")
            val naverResponse = response["response"] as? Map<String, Any>
                ?: throw SocialLoginFailedException("네이버 응답 형식이 올바르지 않습니다.")

            val id = naverResponse["id"]?.toString()
                ?: throw SocialLoginFailedException("네이버 사용자 ID를 가져올 수 없습니다.")

            return SocialUserInfo(
                socialId = id,
                email = naverResponse["email"] as? String,
                nickname = naverResponse["nickname"] as? String,
                profileImageUrl = naverResponse["profile_image"] as? String
            )
        } catch (e: SocialLoginFailedException) {
            throw e
        } catch (e: Exception) {
            throw SocialLoginFailedException("네이버 로그인 처리 중 오류가 발생했습니다: ${e.message}")
        }
    }
}
