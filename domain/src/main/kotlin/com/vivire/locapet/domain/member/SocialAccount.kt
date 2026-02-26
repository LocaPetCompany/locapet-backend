package com.vivire.locapet.domain.member

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "social_accounts")
class SocialAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val provider: SocialProvider,

    @Column(nullable = false)
    val providerUserId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
)
