package com.vivire.locapet.domain.member

import com.vivire.locapet.domain.share.CreatedTraceable
import jakarta.persistence.*

@Entity
@Table(
    name = "identity_verifications",
    indexes = [
        Index(name = "idx_iv_member", columnList = "member_id"),
        Index(name = "idx_iv_transaction", columnList = "transaction_id"),
    ],
)
class IdentityVerification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "member_id")
    val memberId: Long? = null,

    @Column(nullable = false, length = 50)
    val vendor: String,

    @Column(name = "transaction_id", nullable = false)
    val transactionId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: VerificationStatus,

    @Column(name = "ci_hash", length = 128)
    val ciHash: String? = null,
) : CreatedTraceable()

enum class VerificationStatus {
    SUCCESS, FAILED, CI_LOCKED
}
