package com.vivire.locapet.domain.member

import org.springframework.data.jpa.repository.JpaRepository

interface SocialAccountRepository : JpaRepository<SocialAccount, Long> {
    fun findByProviderAndProviderUserId(provider: SocialProvider, providerUserId: String): SocialAccount?
    fun deleteAllByMemberId(memberId: Long)
}
