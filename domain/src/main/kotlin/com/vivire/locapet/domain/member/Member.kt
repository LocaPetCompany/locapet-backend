package com.vivire.locapet.domain.member

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "members")
class Member(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val socialId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val socialProvider: SocialProvider,

    @Column(length = 255)
    var email: String? = null,

    @Column(length = 50)
    var nickname: String? = null,

    @Column(length = 500)
    var profileImageUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: MemberStatus = MemberStatus.PENDING,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: MemberRole = MemberRole.USER,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),

    var withdrawnAt: Instant? = null
) {

    fun completeRegistration(nickname: String) {
        this.nickname = nickname
        this.status = MemberStatus.ACTIVE
        this.updatedAt = Instant.now()
    }

    fun withdraw() {
        this.status = MemberStatus.WITHDRAWN
        this.withdrawnAt = Instant.now()
        this.updatedAt = Instant.now()
    }
}
