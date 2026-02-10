package com.vivire.locapet.domain.meta

import jakarta.persistence.*
import java.time.LocalDateTime

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
    val startTime: LocalDateTime,

    @Column(nullable = false)
    val endTime: LocalDateTime,

    @Column(nullable = false)
    val isActive: Boolean = true,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun isUnderMaintenance(): Boolean {
        val now = LocalDateTime.now()
        return isActive && now.isAfter(startTime) && now.isBefore(endTime)
    }
}
