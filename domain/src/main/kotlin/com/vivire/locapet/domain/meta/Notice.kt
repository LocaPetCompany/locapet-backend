package com.vivire.locapet.domain.meta

import com.vivire.locapet.domain.share.ModifiedTraceable
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "notices",
    indexes = [
        Index(name = "idx_display_priority", columnList = "display_start_time, display_end_time, priority DESC"),
    ],
)
class Notice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val content: String,

    @Column(name = "router_url", length = 500)
    val routerUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_type", nullable = false, length = 20)
    val noticeType: NoticeType,

    @Column(nullable = false)
    val priority: Int = 5,

    @Column(name = "display_start_time", nullable = false)
    val displayStartTime: Instant,

    @Column(name = "display_end_time", nullable = false)
    val displayEndTime: Instant,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
) : ModifiedTraceable() {

    enum class NoticeType {
        INFO, WARNING, URGENT
    }
}
