package com.vivire.locapet.app.api.member.service

import com.vivire.locapet.app.global.auth.RefreshTokenService
import com.vivire.locapet.app.global.exception.MemberNotFoundException
import com.vivire.locapet.domain.member.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val refreshTokenService: RefreshTokenService
) {

    @Transactional
    fun requestWithdrawal(memberId: Long) {
        val member = memberRepository.findById(memberId)
            .orElseThrow { MemberNotFoundException() }

        val effectiveAt = Instant.now().plus(Duration.ofDays(30))
        member.requestWithdrawal(effectiveAt)
        refreshTokenService.delete(memberId)
    }
}
