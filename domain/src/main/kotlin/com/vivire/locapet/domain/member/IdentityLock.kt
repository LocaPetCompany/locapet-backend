package com.vivire.locapet.domain.member

import com.vivire.locapet.domain.share.ModifiedTraceable
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "identity_locks")
class IdentityLock(
    @Id
    @Column(name = "ci_hash", length = 128)
    val ciHash: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "lock_type", nullable = false, length = 20)
    var lockType: IdentityLockType,

    @Column(name = "locked_until")
    var lockedUntil: Instant? = null,

    @Column(nullable = false, length = 50)
    var reason: String,

    @Column(name = "member_id")
    var memberId: Long? = null,
) : ModifiedTraceable() {

    fun isLocked(now: Instant = Instant.now()): Boolean = when (lockType) {
        IdentityLockType.PERMANENT -> true
        IdentityLockType.ACTIVE_ACCOUNT -> true
        IdentityLockType.TEMPORARY -> lockedUntil != null && now.isBefore(lockedUntil)
    }
}
