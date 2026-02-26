package com.vivire.locapet.domain.member

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "members")
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var accountStatus: AccountStatus = AccountStatus.ACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    var onboardingStage: OnboardingStage = OnboardingStage.PROFILE_REQUIRED,

    @Column(length = 128)
    val ciHash: String? = null,

    @Column(length = 20)
    val phone: String? = null,

    @Column(length = 100)
    val name: String? = null,

    val birth: LocalDate? = null,

    @Column(length = 50)
    var nickname: String? = null,

    @Column(length = 255)
    var email: String? = null,

    @Column(length = 500)
    var profileImageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: MemberRole = MemberRole.USER,

    @Column(nullable = false)
    var termsOfServiceAgreed: Boolean = false,

    @Column(nullable = false)
    var privacyPolicyAgreed: Boolean = false,

    @Column(nullable = false)
    var marketingConsent: Boolean = false,

    var termsAgreedAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var withdrawalType: WithdrawalType? = null,

    var withdrawalRequestedAt: Instant? = null,
    var withdrawalEffectiveAt: Instant? = null,
    var withdrawnAt: Instant? = null,
    var forceWithdrawnAt: Instant? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
) {

    fun completeProfile(nickname: String, tos: Boolean, privacy: Boolean, marketing: Boolean) {
        this.nickname = nickname
        this.termsOfServiceAgreed = tos
        this.privacyPolicyAgreed = privacy
        this.marketingConsent = marketing
        this.termsAgreedAt = Instant.now()
        this.onboardingStage = OnboardingStage.COMPLETED
        this.updatedAt = Instant.now()
    }

    fun requestWithdrawal(effectiveAt: Instant) {
        this.accountStatus = AccountStatus.WITHDRAW_REQUESTED
        this.withdrawalType = WithdrawalType.VOLUNTARY
        this.withdrawalRequestedAt = Instant.now()
        this.withdrawalEffectiveAt = effectiveAt
        this.updatedAt = Instant.now()
    }

    fun cancelWithdrawal() {
        this.accountStatus = AccountStatus.ACTIVE
        this.withdrawalType = null
        this.withdrawalRequestedAt = null
        this.withdrawalEffectiveAt = null
        this.updatedAt = Instant.now()
    }

    fun completeWithdrawal() {
        this.accountStatus = AccountStatus.WITHDRAWN
        this.withdrawnAt = Instant.now()
        this.updatedAt = Instant.now()
    }

    fun forceWithdraw() {
        this.accountStatus = AccountStatus.FORCE_WITHDRAWN
        this.withdrawalType = WithdrawalType.FORCED
        this.forceWithdrawnAt = Instant.now()
        this.updatedAt = Instant.now()
    }

    fun reactivateForRejoin() {
        this.accountStatus = AccountStatus.ACTIVE
        this.onboardingStage = OnboardingStage.PROFILE_REQUIRED
        this.nickname = null
        this.email = null
        this.profileImageUrl = null
        this.termsOfServiceAgreed = false
        this.privacyPolicyAgreed = false
        this.marketingConsent = false
        this.termsAgreedAt = null
        this.withdrawalType = null
        this.withdrawalRequestedAt = null
        this.withdrawalEffectiveAt = null
        this.withdrawnAt = null
        this.updatedAt = Instant.now()
    }
}
