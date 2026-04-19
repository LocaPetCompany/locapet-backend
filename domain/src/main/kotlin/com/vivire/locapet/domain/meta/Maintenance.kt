package com.vivire.locapet.domain.meta

import com.vivire.locapet.domain.share.ModifiedTraceable
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "maintenances",
    indexes = [
        Index(name = "idx_time_active", columnList = "start_time, end_time, is_active"),
    ],
)
class Maintenance(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(name = "start_time", nullable = false)
    val startTime: Instant,

    @Column(name = "end_time", nullable = false)
    val endTime: Instant,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
) : ModifiedTraceable() {

    fun isUnderMaintenance(): Boolean {
        val now = Instant.now()
        return isActive && now.isAfter(startTime) && now.isBefore(endTime)
    }
}
