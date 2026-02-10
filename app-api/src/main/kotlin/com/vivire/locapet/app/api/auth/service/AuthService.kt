package com.vivire.locapet.app.api.auth.service

import com.vivire.locapet.app.api.auth.dto.*
import com.vivire.locapet.app.global.auth.JwtTokenProvider
import com.vivire.locapet.app.global.auth.RefreshTokenService
import com.vivire.locapet.app.global.auth.social.SocialLoginClientResolver
import com.vivire.locapet.app.global.exception.InvalidRefreshTokenException
import com.vivire.locapet.app.global.exception.MemberNotFoundException
import com.vivire.locapet.app.global.exception.MemberWithdrawnException
import com.vivire.locapet.domain.member.Member
import com.vivire.locapet.domain.member.MemberRepository
import com.vivire.locapet.domain.member.MemberStatus
import com.vivire.locapet.domain.member.SocialProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val socialLoginClientResolver: SocialLoginClientResolver,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenService: RefreshTokenService,
    private val memberRepository: MemberRepository
) {

    @Transactional
    fun signIn(request: SignInRequest): SignInResponse {
        val client = socialLoginClientResolver.resolve(request.provider)
        val socialUserInfo = client.getUserInfo(request.socialToken)

        val member = memberRepository.findBySocialIdAndSocialProvider(
            socialUserInfo.socialId,
            request.provider
        )

        if (member == null) {
            // 신규 회원 — PENDING 상태로 생성 후 임시 토큰 발급
            val newMember = Member(
                socialId = socialUserInfo.socialId,
                socialProvider = request.provider,
                email = socialUserInfo.email,
                profileImageUrl = socialUserInfo.profileImageUrl
            )
            memberRepository.save(newMember)

            val temporaryToken = jwtTokenProvider.createTemporaryToken(
                socialId = socialUserInfo.socialId,
                provider = request.provider,
                email = socialUserInfo.email,
                profileImageUrl = socialUserInfo.profileImageUrl
            )

            return SignInResponse(
                isNewUser = true,
                temporaryToken = temporaryToken
            )
        }

        return when (member.status) {
            MemberStatus.ACTIVE -> {
                val accessToken = jwtTokenProvider.createAccessToken(member.id!!, member.role.name)
                val refreshToken = jwtTokenProvider.createRefreshToken(member.id!!)
                refreshTokenService.save(member.id!!, refreshToken)

                SignInResponse(
                    isNewUser = false,
                    accessToken = accessToken,
                    refreshToken = refreshToken
                )
            }

            MemberStatus.PENDING -> {
                val temporaryToken = jwtTokenProvider.createTemporaryToken(
                    socialId = socialUserInfo.socialId,
                    provider = request.provider,
                    email = socialUserInfo.email,
                    profileImageUrl = socialUserInfo.profileImageUrl
                )

                SignInResponse(
                    isNewUser = true,
                    temporaryToken = temporaryToken
                )
            }

            MemberStatus.WITHDRAWN -> throw MemberWithdrawnException()
        }
    }

    fun reissue(request: ReissueRequest): ReissueResponse {
        val claims = jwtTokenProvider.validateAndGetClaims(request.refreshToken)
        val tokenType = claims["type"] as? String
        if (tokenType != "REFRESH") {
            throw InvalidRefreshTokenException()
        }

        val memberId = claims.subject.toLong()

        if (!refreshTokenService.matches(memberId, request.refreshToken)) {
            throw InvalidRefreshTokenException()
        }

        val member = memberRepository.findById(memberId)
            .orElseThrow { MemberNotFoundException() }

        // Refresh Token Rotation
        refreshTokenService.delete(memberId)

        val newAccessToken = jwtTokenProvider.createAccessToken(member.id!!, member.role.name)
        val newRefreshToken = jwtTokenProvider.createRefreshToken(member.id!!)
        refreshTokenService.save(member.id!!, newRefreshToken)

        return ReissueResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken
        )
    }

    fun logout(memberId: Long) {
        refreshTokenService.delete(memberId)
    }
}
