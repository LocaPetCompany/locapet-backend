package com.vivire.locapet.domain.meta

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface NoticeRepository : JpaRepository<Notice, Long> {
    @Query("""
        SELECT n FROM Notice n
        WHERE n.isActive = true
        AND n.displayStartTime <= :now
        AND n.displayEndTime >= :now
        ORDER BY n.priority DESC, n.createdAt DESC
    """)
    fun findActiveNotices(now: LocalDateTime = LocalDateTime.now()): List<Notice>
}
