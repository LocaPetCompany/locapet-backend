package com.vivire.locapet.app.api.auth.service

import com.vivire.locapet.app.api.auth.dto.*
import com.vivire.locapet.app.global.auth.JwtTokenProvider
import com.vivire.locapet.app.global.auth.RefreshTokenService
import com.vivire.locapet.app.global.auth.onboarding.OnboardingSession
import com.vivire.locapet.app.global.auth.onboarding.OnboardingSessionService
import com.vivire.locapet.app.global.auth.social.SocialLoginClientResolver
import com.vivire.locapet.app.global.exception.*
import com.vivire.locapet.domain.member.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class AuthService(
    private val socialLoginClientResolver: SocialLoginClientResolver,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenService: RefreshTokenService,
    private val socialAccountRepository: SocialAccountRepository,
    private val memberRepository: MemberRepository,
    private val onboardingSessionService: OnboardingSessionService
) {

    @Transactional
    fun socialLogin(provider: SocialProvider, request: SocialLoginRequest): SocialLoginResponse {
        val client = socialLoginClientResolver.resolve(provider)
        val socialUserInfo = client.getUserInfo(request.socialToken)

        val socialAccount = socialAccountRepository.findByProviderAndProviderUserId(
            provider, socialUserInfo.socialId
        )

        if (socialAccount == null) {
            val session = OnboardingSession(
                socialId = socialUserInfo.socialId,
                provider = provider,
                email = socialUserInfo.email,
                profileImageUrl = socialUserInfo.profileImageUrl
            )
            val onboardingToken = onboardingSessionService.create(session)
            return SocialLoginResponse(
                result = SocialLoginResult.NEEDS_IDENTITY_VERIFICATION,
                onboardingToken = onboardingToken
            )
        }

        val member = socialAccount.member
        return handleExistingMember(member, provider, socialUserInfo.socialId, socialUserInfo.email, socialUserInfo.profileImageUrl)
    }

    private fun handleExistingMember(
        member: Member,
        provider: SocialProvider,
        socialId: String,
        email: String?,
        profileImageUrl: String?
    ): SocialLoginResponse {
        return when (member.accountStatus) {
            AccountStatus.ACTIVE -> {
                when (member.onboardingStage) {
                    OnboardingStage.COMPLETED -> {
                        val accessToken = jwtTokenProvider.createAccessToken(member.id!!, member.role.name)
                        val refreshToken = jwtTokenProvider.createRefreshToken(member.id!!)
                        refreshTokenService.save(member.id!!, refreshToken)
                        SocialLoginResponse(
                            result = SocialLoginResult.COMPLETED,
                            accessToken = accessToken,
                            refreshToken = refreshToken
                        )
                    }
                    OnboardingStage.PROFILE_REQUIRED -> {
                        val onboardingAccessToken = jwtTokenProvider.createOnboardingAccessToken(member.id!!)
                        SocialLoginResponse(
                            result = SocialLoginResult.NEEDS_PROFILE,
                            onboardingAccessToken = onboardingAccessToken
                        )
                    }
                    OnboardingStage.IDENTITY_REQUIRED -> {
                        throw OnboardingStageInvalidException()
                    }
                }
            }

            AccountStatus.WITHDRAW_REQUESTED -> {
                member.cancelWithdrawal()
                val accessToken = jwtTokenProvider.createAccessToken(member.id!!, member.role.name)
                val refreshToken = jwtTokenProvider.createRefreshToken(member.id!!)
                refreshTokenService.save(member.id!!, refreshToken)
                SocialLoginResponse(
                    result = SocialLoginResult.COMPLETED,
                    accessToken = accessToken,
                    refreshToken = refreshToken
                )
            }

            AccountStatus.WITHDRAWN -> {
                val withdrawnAt = member.withdrawnAt ?: throw MemberWithdrawnException()
                val daysSinceWithdrawal = Duration.between(withdrawnAt, Instant.now()).toDays()
                if (daysSinceWithdrawal < 30) {
                    throw RejoinCooldownException()
                }
                val session = OnboardingSession(
                    socialId = socialId,
                    provider = provider,
                    email = email,
                    profileImageUrl = profileImageUrl
                )
                val onboardingToken = onboardingSessionService.create(session)
                SocialLoginResponse(
                    result = SocialLoginResult.NEEDS_IDENTITY_VERIFICATION,
                    onboardingToken = onboardingToken
                )
            }

            AccountStatus.FORCE_WITHDRAWN -> {
                throw PermanentlyBannedException()
            }
        }
    }

    @Transactional
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

        when (member.accountStatus) {
            AccountStatus.WITHDRAWN -> {
                refreshTokenService.delete(memberId)
                throw MemberWithdrawnException()
            }
            AccountStatus.FORCE_WITHDRAWN -> {
                refreshTokenService.delete(memberId)
                throw PermanentlyBannedException()
            }
            AccountStatus.WITHDRAW_REQUESTED -> {
                member.cancelWithdrawal()
            }
            AccountStatus.ACTIVE -> { /* proceed */ }
        }

        // Refresh Token Rotation
        refreshTokenService.delete(memberId)
        val newAccessToken = jwtTokenProvider.createAccessToken(member.id!!, member.role.name)
        val newRefreshToken = jwtTokenProvider.createRefreshToken(member.id!!)
        refreshTokenService.save(member.id!!, newRefreshToken)

        return ReissueResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            accountStatus = member.accountStatus,
            onboardingStage = member.onboardingStage
        )
    }

    @Transactional(readOnly = true)
    fun getSession(memberId: Long): SessionResponse {
        val member = memberRepository.findById(memberId)
            .orElseThrow { MemberNotFoundException() }

        when (member.accountStatus) {
            AccountStatus.WITHDRAWN -> throw MemberWithdrawnException()
            AccountStatus.FORCE_WITHDRAWN -> throw PermanentlyBannedException()
            else -> { /* proceed */ }
        }

        return SessionResponse(
            memberId = member.id!!,
            accountStatus = member.accountStatus,
            onboardingStage = member.onboardingStage,
            nickname = member.nickname
        )
    }

    fun logout(memberId: Long) {
        refreshTokenService.delete(memberId)
    }
}
