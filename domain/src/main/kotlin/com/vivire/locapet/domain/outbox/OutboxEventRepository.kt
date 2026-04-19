package com.vivire.locapet.domain.outbox

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface OutboxEventRepository : JpaRepository<OutboxEvent, Long> {

    fun findAllByStatusOrderByCreatedAtAsc(
        status: OutboxEventStatus,
        pageable: Pageable,
    ): List<OutboxEvent>

    fun existsByDedupeKey(dedupeKey: String): Boolean

    fun findByDedupeKey(dedupeKey: String): OutboxEvent?

    @Query(
        """
        SELECT COUNT(e) FROM OutboxEvent e
        WHERE e.status = :status
          AND e.createdAt < :before
        """
    )
    fun countByStatusBefore(
        @Param("status") status: OutboxEventStatus,
        @Param("before") before: Instant,
    ): Long
}
