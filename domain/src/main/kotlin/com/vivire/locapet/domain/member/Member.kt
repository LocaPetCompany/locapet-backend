package com.vivire.locapet.domain.member

import com.vivire.locapet.domain.share.ModifiedTraceable
import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "members",
    indexes = [
        Index(name = "idx_members_account_status", columnList = "account_status"),
        Index(name = "idx_members_nickname", columnList = "nickname"),
    ],
    // uk_members_ci_hash UNIQUE (ci_hash) WHERE ci_hash IS NOT NULL 는 부분 인덱스라
    // JPA @UniqueConstraint 로 표기 불가. V004 마이그레이션 참조.
)
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 30)
    var accountStatus: AccountStatus = AccountStatus.ACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_stage", nullable = false, length = 30)
    var onboardingStage: OnboardingStage = OnboardingStage.PROFILE_REQUIRED,

    @Column(name = "ci_hash", length = 128)
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

    @Column(name = "profile_image_url", length = 500)
    var profileImageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: MemberRole = MemberRole.USER,

    @Column(name = "terms_of_service_agreed", nullable = false)
    var termsOfServiceAgreed: Boolean = false,

    @Column(name = "privacy_policy_agreed", nullable = false)
    var privacyPolicyAgreed: Boolean = false,

    @Column(name = "marketing_consent", nullable = false)
    var marketingConsent: Boolean = false,

    @Column(name = "terms_agreed_at")
    var termsAgreedAt: Instant? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_type", length = 20)
    var withdrawalType: WithdrawalType? = null,

    @Column(name = "withdrawal_requested_at")
    var withdrawalRequestedAt: Instant? = null,

    @Column(name = "withdrawal_effective_at")
    var withdrawalEffectiveAt: Instant? = null,

    @Column(name = "withdrawn_at")
    var withdrawnAt: Instant? = null,

    @Column(name = "force_withdrawn_at")
    var forceWithdrawnAt: Instant? = null,
) : ModifiedTraceable() {

    fun completeProfile(nickname: String, tos: Boolean, privacy: Boolean, marketing: Boolean) {
        this.nickname = nickname
        this.termsOfServiceAgreed = tos
        this.privacyPolicyAgreed = privacy
        this.marketingConsent = marketing
        this.termsAgreedAt = Instant.now()
        this.onboardingStage = OnboardingStage.COMPLETED
    }

    fun requestWithdrawal(effectiveAt: Instant) {
        this.accountStatus = AccountStatus.WITHDRAW_REQUESTED
        this.withdrawalType = WithdrawalType.VOLUNTARY
        this.withdrawalRequestedAt = Instant.now()
        this.withdrawalEffectiveAt = effectiveAt
    }

    fun cancelWithdrawal() {
        this.accountStatus = AccountStatus.ACTIVE
        this.withdrawalType = null
        this.withdrawalRequestedAt = null
        this.withdrawalEffectiveAt = null
    }

    fun completeWithdrawal() {
        this.accountStatus = AccountStatus.WITHDRAWN
        this.withdrawnAt = Instant.now()
    }

    fun forceWithdraw() {
        this.accountStatus = AccountStatus.FORCE_WITHDRAWN
        this.withdrawalType = WithdrawalType.FORCED
        this.forceWithdrawnAt = Instant.now()
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
    }
}
