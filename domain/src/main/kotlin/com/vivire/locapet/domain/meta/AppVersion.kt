package com.vivire.locapet.domain.meta

import com.vivire.locapet.domain.share.ModifiedTraceable
import jakarta.persistence.*

@Entity
@Table(
    name = "app_versions",
    indexes = [
        Index(name = "idx_platform_active", columnList = "platform, is_active"),
    ],
)
class AppVersion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 20)
    val version: String,

    @Column(name = "force_update", nullable = false)
    val forceUpdate: Boolean = false,

    @Column(name = "minimum_version", length = 20)
    val minimumVersion: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val platform: Platform,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
) : ModifiedTraceable() {

    enum class Platform {
        IOS, ANDROID, ALL
    }
}
