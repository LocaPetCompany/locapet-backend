package com.vivire.locapet.app.api.onboarding.service

import com.vivire.locapet.app.api.onboarding.dto.*
import com.vivire.locapet.app.global.auth.JwtTokenProvider
import com.vivire.locapet.app.global.auth.RefreshTokenService
import com.vivire.locapet.app.global.auth.identity.CiHashProvider
import com.vivire.locapet.app.global.auth.identity.IdentityVerificationProvider
import com.vivire.locapet.app.global.auth.onboarding.OnboardingSessionService
import com.vivire.locapet.app.global.exception.*
import com.vivire.locapet.domain.member.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OnboardingService(
    private val onboardingSessionService: OnboardingSessionService,
    private val identityVerificationProvider: IdentityVerificationProvider,
    private val ciHashProvider: CiHashProvider,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenService: RefreshTokenService,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val identityLockRepository: IdentityLockRepository,
    private val identityVerificationRepository: IdentityVerificationRepository
) {

    @Transactional
    fun verifyIdentity(request: IdentityVerifyRequest): IdentityVerifyResponse {
        val session = onboardingSessionService.consume(request.onboardingToken)
            ?: throw OnboardingSessionExpiredException()

        val result = try {
            identityVerificationProvider.verify(request.transactionId)
        } catch (e: Exception) {
            throw IdentityVerificationFailedException()
        }

        val ciHash = ciHashProvider.hash(result.ci)
        val existingLock = identityLockRepository.findById(ciHash).orElse(null)

        if (existingLock != null && existingLock.isLocked()) {
            return handleLockedIdentity(existingLock, ciHash, request.transactionId, session, result)
        }

        if (existingLock != null && !existingLock.isLocked()) {
            // TEMPORARY 만료 = 재가입 허용
            return handleRejoin(existingLock, ciHash, request.transactionId, session, result)
        }

        // 완전 신규
        return handleNewUser(ciHash, request.transactionId, session, result)
    }

    private fun handleLockedIdentity(
        lock: IdentityLock,
        ciHash: String,
        transactionId: String,
        session: com.vivire.locapet.app.global.auth.onboarding.OnboardingSession,
        result: com.vivire.locapet.app.global.auth.identity.IdentityVerificationResult
    ): IdentityVerifyResponse {
        return when (lock.lockType) {
            IdentityLockType.PERMANENT -> {
                saveVerificationLog(null, transactionId, VerificationStatus.CI_LOCKED, ciHash)
                throw PermanentlyBannedException()
            }
            IdentityLockType.TEMPORARY -> {
                saveVerificationLog(null, transactionId, VerificationStatus.CI_LOCKED, ciHash)
                throw RejoinCooldownException()
            }
            IdentityLockType.ACTIVE_ACCOUNT -> {
                // 기존 활성 회원 — 소셜 계정 추가 연동
                val member = memberRepository.findByCiHash(ciHash)
                    ?: throw MemberNotFoundException()

                val newSocialAccount = SocialAccount(
                    provider = session.provider,
                    providerUserId = session.socialId,
                    member = member
                )
                socialAccountRepository.save(newSocialAccount)
                saveVerificationLog(member.id, transactionId, VerificationStatus.SUCCESS, ciHash)

                when (member.onboardingStage) {
                    OnboardingStage.COMPLETED -> {
                        val accessToken = jwtTokenProvider.createAccessToken(member.id!!, member.role.name)
                        val refreshToken = jwtTokenProvider.createRefreshToken(member.id!!)
                        refreshTokenService.save(member.id!!, refreshToken)
                        IdentityVerifyResponse(
                            result = IdentityVerifyResult.COMPLETED,
                            accessToken = accessToken,
                            refreshToken = refreshToken
                        )
                    }
                    OnboardingStage.PROFILE_REQUIRED -> {
                        val onboardingAccessToken = jwtTokenProvider.createOnboardingAccessToken(member.id!!)
                        IdentityVerifyResponse(
                            result = IdentityVerifyResult.NEEDS_PROFILE,
                            onboardingAccessToken = onboardingAccessToken
                        )
                    }
                    OnboardingStage.IDENTITY_REQUIRED -> throw OnboardingStageInvalidException()
                }
            }
        }
    }

    private fun handleRejoin(
        lock: IdentityLock,
        ciHash: String,
        transactionId: String,
        session: com.vivire.locapet.app.global.auth.onboarding.OnboardingSession,
        result: com.vivire.locapet.app.global.auth.identity.IdentityVerificationResult
    ): IdentityVerifyResponse {
        val member = memberRepository.findByCiHash(ciHash)
            ?: throw MemberNotFoundException()

        member.reactivateForRejoin()

        // 기존 소셜 계정 전부 삭제 후 새로 생성
        socialAccountRepository.deleteAllByMemberId(member.id!!)
        val newSocialAccount = SocialAccount(
            provider = session.provider,
            providerUserId = session.socialId,
            member = member
        )
        socialAccountRepository.save(newSocialAccount)

        // identity_lock 갱신
        lock.lockType = IdentityLockType.ACTIVE_ACCOUNT
        lock.lockedUntil = null
        lock.reason = "ACTIVE_ACCOUNT"
        lock.memberId = member.id
        lock.updatedAt = java.time.Instant.now()

        saveVerificationLog(member.id, transactionId, VerificationStatus.SUCCESS, ciHash)

        val onboardingAccessToken = jwtTokenProvider.createOnboardingAccessToken(member.id!!)
        return IdentityVerifyResponse(
            result = IdentityVerifyResult.NEEDS_PROFILE,
            onboardingAccessToken = onboardingAccessToken
        )
    }

    private fun handleNewUser(
        ciHash: String,
        transactionId: String,
        session: com.vivire.locapet.app.global.auth.onboarding.OnboardingSession,
        result: com.vivire.locapet.app.global.auth.identity.IdentityVerificationResult
    ): IdentityVerifyResponse {
        val member = Member(
            ciHash = ciHash,
            phone = result.phone,
            name = result.name,
            birth = result.birthDate,
            email = session.email,
            profileImageUrl = session.profileImageUrl
        )
        memberRepository.save(member)

        val socialAccount = SocialAccount(
            provider = session.provider,
            providerUserId = session.socialId,
            member = member
        )
        socialAccountRepository.save(socialAccount)

        val identityLock = IdentityLock(
            ciHash = ciHash,
            lockType = IdentityLockType.ACTIVE_ACCOUNT,
            reason = "ACTIVE_ACCOUNT",
            memberId = member.id
        )
        identityLockRepository.save(identityLock)

        saveVerificationLog(member.id, transactionId, VerificationStatus.SUCCESS, ciHash)

        val onboardingAccessToken = jwtTokenProvider.createOnboardingAccessToken(member.id!!)
        return IdentityVerifyResponse(
            result = IdentityVerifyResult.NEEDS_PROFILE,
            onboardingAccessToken = onboardingAccessToken
        )
    }

    @Transactional
    fun completeProfile(request: ProfileCompleteRequest): ProfileCompleteResponse {
        val claims = jwtTokenProvider.validateAndGetClaims(request.onboardingAccessToken)
        val tokenType = claims["type"] as? String
        if (tokenType != "ONBOARDING") {
            throw InvalidTokenException("유효하지 않은 온보딩 토큰입니다.")
        }

        val memberId = claims.subject.toLong()
        val member = memberRepository.findById(memberId)
            .orElseThrow { MemberNotFoundException() }

        if (member.onboardingStage != OnboardingStage.PROFILE_REQUIRED) {
            throw OnboardingStageInvalidException()
        }

        if (memberRepository.existsByNickname(request.nickname)) {
            throw DuplicateNicknameException()
        }

        member.completeProfile(
            nickname = request.nickname,
            tos = request.termsOfServiceAgreed,
            privacy = request.privacyPolicyAgreed,
            marketing = request.marketingConsent
        )

        val accessToken = jwtTokenProvider.createAccessToken(member.id!!, member.role.name)
        val refreshToken = jwtTokenProvider.createRefreshToken(member.id!!)
        refreshTokenService.save(member.id!!, refreshToken)

        return ProfileCompleteResponse(
            memberId = member.id!!,
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    private fun saveVerificationLog(
        memberId: Long?,
        transactionId: String,
        status: VerificationStatus,
        ciHash: String?
    ) {
        val verification = IdentityVerification(
            memberId = memberId,
            vendor = "PASS",
            transactionId = transactionId,
            status = status,
            ciHash = ciHash
        )
        identityVerificationRepository.save(verification)
    }
}
