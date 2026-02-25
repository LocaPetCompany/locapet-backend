package com.vivire.locapet.domain.meta

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface MaintenanceRepository : JpaRepository<Maintenance, Long> {
    @Query("""
        SELECT m FROM Maintenance m
        WHERE m.isActive = true
        AND m.startTime <= :now
        AND m.endTime >= :now
        ORDER BY m.createdAt DESC
    """)
    fun findActiveMaintenances(now: Instant = Instant.now()): List<Maintenance>
}
