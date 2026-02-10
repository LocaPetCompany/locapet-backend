package com.vivire.locapet.domain.meta

import jakarta.persistence.*
import java.time.LocalDateTime

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
    val displayStartTime: LocalDateTime,

    @Column(nullable = false)
    val displayEndTime: LocalDateTime,

    @Column(nullable = false)
    val isActive: Boolean = true,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    enum class NoticeType {
        INFO, WARNING, URGENT
    }
}
