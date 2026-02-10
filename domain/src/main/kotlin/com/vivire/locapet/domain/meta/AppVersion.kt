package com.vivire.locapet.domain.meta

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "app_versions")
class AppVersion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 20)
    val version: String,

    @Column(nullable = false)
    val forceUpdate: Boolean = false,

    @Column(length = 20)
    val minimumVersion: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val platform: Platform,

    @Column(nullable = false)
    val isActive: Boolean = true,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {

    enum class Platform {
        IOS, ANDROID, ALL
    }

}
