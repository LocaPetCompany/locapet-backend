package com.vivire.locapet.app.global.auth.social

import com.vivire.locapet.app.global.exception.SocialLoginFailedException
import com.vivire.locapet.common.config.AuthConfigProperties
import com.vivire.locapet.domain.member.SocialProvider
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class KakaoLoginClient(
    private val authConfigProperties: AuthConfigProperties
) : SocialLoginClient {

    private val restClient = RestClient.create()

    override fun provider(): SocialProvider = SocialProvider.KAKAO

    override fun getUserInfo(token: String): SocialUserInfo {
        try {
            val response = restClient.get()
                .uri(authConfigProperties.social.kakao.userInfoUrl)
                .header("Authorization", "Bearer $token")
                .retrieve()
                .body(Map::class.java)
                ?: throw SocialLoginFailedException("카카오 사용자 정보를 가져올 수 없습니다.")

            val id = response["id"]?.toString()
                ?: throw SocialLoginFailedException("카카오 사용자 ID를 가져올 수 없습니다.")

            @Suppress("UNCHECKED_CAST")
            val kakaoAccount = response["kakao_account"] as? Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val profile = kakaoAccount?.get("profile") as? Map<String, Any>

            return SocialUserInfo(
                socialId = id,
                email = kakaoAccount?.get("email") as? String,
                nickname = profile?.get("nickname") as? String,
                profileImageUrl = profile?.get("profile_image_url") as? String
            )
        } catch (e: SocialLoginFailedException) {
            throw e
        } catch (e: Exception) {
            throw SocialLoginFailedException("카카오 로그인 처리 중 오류가 발생했습니다: ${e.message}")
        }
    }
}
