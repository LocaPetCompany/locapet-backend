package com.vivire.locapet.domain.member

import com.vivire.locapet.domain.share.CreatedTraceable
import jakarta.persistence.*

@Entity
@Table(
    name = "social_accounts",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_social_provider_user", columnNames = ["provider", "provider_user_id"]),
        UniqueConstraint(name = "uk_social_provider_member", columnNames = ["provider", "member_id"]),
    ],
)
class SocialAccount(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val provider: SocialProvider,

    @Column(name = "provider_user_id", nullable = false)
    val providerUserId: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    val member: Member,
) : CreatedTraceable()
