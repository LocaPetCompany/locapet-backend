package com.vivire.locapet.domain.meta

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "maintenances")
class Maintenance(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(nullable = false)
    val startTime: Instant,

    @Column(nullable = false)
    val endTime: Instant,

    @Column(nullable = false)
    val isActive: Boolean = true,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun isUnderMaintenance(): Boolean {
        val now = Instant.now()
        return isActive && now.isAfter(startTime) && now.isBefore(endTime)
    }
}
