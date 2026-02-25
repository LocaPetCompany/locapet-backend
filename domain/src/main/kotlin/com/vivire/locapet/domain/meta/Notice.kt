package com.vivire.locapet.domain.meta

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "notices")
class Notice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(length = 500)
    val routerUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val noticeType: NoticeType,

    @Column(nullable = false)
    val priority: Int = 5,

    @Column(nullable = false)
    val displayStartTime: Instant,

    @Column(nullable = false)
    val displayEndTime: Instant,

    @Column(nullable = false)
    val isActive: Boolean = true,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    enum class NoticeType {
        INFO, WARNING, URGENT
    }
}
