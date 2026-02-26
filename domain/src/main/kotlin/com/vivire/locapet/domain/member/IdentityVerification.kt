package com.vivire.locapet.domain.member

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "identity_verifications")
class IdentityVerification(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    val memberId: Long? = null,

    @Column(nullable = false, length = 50)
    val vendor: String,

    @Column(nullable = false)
    val transactionId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: VerificationStatus,

    @Column(length = 128)
    val ciHash: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)

enum class VerificationStatus {
    SUCCESS, FAILED, CI_LOCKED
}
