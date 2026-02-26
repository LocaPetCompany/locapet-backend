package com.vivire.locapet.domain.member

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "identity_locks")
class IdentityLock(
    @Id
    @Column(length = 128)
    val ciHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var lockType: IdentityLockType,

    var lockedUntil: Instant? = null,

    @Column(nullable = false, length = 50)
    var reason: String,

    var memberId: Long? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
) {

    fun isLocked(now: Instant = Instant.now()): Boolean = when (lockType) {
        IdentityLockType.PERMANENT -> true
        IdentityLockType.ACTIVE_ACCOUNT -> true
        IdentityLockType.TEMPORARY -> lockedUntil != null && now.isBefore(lockedUntil)
    }
}
