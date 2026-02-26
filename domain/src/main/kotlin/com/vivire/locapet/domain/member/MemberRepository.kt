package com.vivire.locapet.domain.member

import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {
    fun findByCiHash(ciHash: String): Member?
    fun existsByNickname(nickname: String): Boolean
}
