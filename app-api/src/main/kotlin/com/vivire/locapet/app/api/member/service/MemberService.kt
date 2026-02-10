package com.vivire.locapet.app.api.member.service

import com.vivire.locapet.app.api.member.dto.SignUpRequest
import com.vivire.locapet.app.api.member.dto.SignUpResponse
import com.vivire.locapet.app.global.auth.JwtTokenProvider
import com.vivire.locapet.app.global.auth.RefreshTokenService
import com.vivire.locapet.app.global.exception.DuplicateNicknameException
import com.vivire.locapet.app.global.exception.InvalidTokenException
import com.vivire.locapet.app.global.exception.MemberNotFoundException
import com.vivire.locapet.domain.member.MemberRepository
import com.vivire.locapet.domain.member.MemberStatus
import com.vivire.locapet.domain.member.SocialProvider
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenService: RefreshTokenService
) {

    @Transactional
    fun signUp(request: SignUpRequest): SignUpResponse {
        val claims = jwtTokenProvider.validateAndGetClaims(request.temporaryToken)
        val tokenType = claims["type"] as? String
        if (tokenType != "TEMPORARY") {
            throw InvalidTokenException("유효하지 않은 임시 토큰입니다.")
        }

        val socialId = claims.subject
        val provider = SocialProvider.valueOf(claims["provider"] as String)

        if (memberRepository.existsByNickname(request.nickname)) {
            throw DuplicateNicknameException()
        }

        val member = memberRepository.findBySocialIdAndSocialProvider(socialId, provider)
            ?: throw MemberNotFoundException("회원 정보를 찾을 수 없습니다. 다시 로그인해주세요.")

        if (member.status != MemberStatus.PENDING) {
            throw InvalidTokenException("이미 가입이 완료된 회원입니다.")
        }

        member.completeRegistration(request.nickname)

        val accessToken = jwtTokenProvider.createAccessToken(member.id!!, member.role.name)
        val refreshToken = jwtTokenProvider.createRefreshToken(member.id!!)
        refreshTokenService.save(member.id!!, refreshToken)

        return SignUpResponse(
            memberId = member.id!!,
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }
}
