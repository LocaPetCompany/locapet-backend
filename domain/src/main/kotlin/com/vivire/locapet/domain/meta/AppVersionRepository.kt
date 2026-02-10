package com.vivire.locapet.domain.meta

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AppVersionRepository : JpaRepository<AppVersion, Long> {
    fun findFirstByIsActiveTrueAndPlatformOrderByCreatedAtDesc(platform: AppVersion.Platform): AppVersion?

    fun findFirstByIsActiveTrueAndPlatformInOrderByCreatedAtDesc(platforms: List<AppVersion.Platform>): AppVersion?
}
