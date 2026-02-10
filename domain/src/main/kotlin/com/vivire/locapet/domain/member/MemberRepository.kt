package com.vivire.locapet.domain.member

import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {
    fun findBySocialIdAndSocialProvider(socialId: String, socialProvider: SocialProvider): Member?
    fun existsByNickname(nickname: String): Boolean
}
