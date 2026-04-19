package com.vivire.locapet.domain.outbox

import com.vivire.locapet.domain.share.CreatedTraceable
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "outbox_events",
    indexes = [
        Index(name = "idx_outbox_pending_created", columnList = "created_at"),
    ],
    // uk_outbox_dedupe_key UNIQUE (dedupe_key) WHERE dedupe_key IS NOT NULL
    // 부분 유니크 인덱스라 JPA @UniqueConstraint 로 표기 불가. V006 마이그레이션 참조.
)
class OutboxEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "aggregate_type", nullable = false, length = 50)
    val aggregateType: String,

    @Column(name = "aggregate_id", nullable = false)
    val aggregateId: Long,

    @Column(name = "event_type", nullable = false, length = 50)
    val eventType: String,

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    val payload: Map<String, Any?>,

    @Column(name = "dedupe_key", length = 200)
    val dedupeKey: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OutboxEventStatus = OutboxEventStatus.PENDING,

    @Column(name = "attempt_count", nullable = false)
    var attemptCount: Int = 0,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "published_at")
    var publishedAt: Instant? = null,
) : CreatedTraceable() {

    fun markPublished() {
        this.status = OutboxEventStatus.PUBLISHED
        this.publishedAt = Instant.now()
        this.lastError = null
    }

    fun recordFailure(error: String) {
        this.attemptCount += 1
        this.lastError = error.take(2000)
        this.status = OutboxEventStatus.FAILED
    }

    fun recordAttempt() {
        this.attemptCount += 1
    }
}
