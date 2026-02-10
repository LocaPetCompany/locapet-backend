package com.vivire.locapet.app.api.meta.service

import com.vivire.locapet.app.api.meta.dto.*
import com.vivire.locapet.common.config.MetaConfigProperties
import com.vivire.locapet.domain.meta.AppVersion
import com.vivire.locapet.domain.meta.AppVersionRepository
import com.vivire.locapet.domain.meta.MaintenanceRepository
import com.vivire.locapet.domain.meta.NoticeRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class MetaService(
    private val appVersionRepository: AppVersionRepository,
    private val maintenanceRepository: MaintenanceRepository,
    private val noticeRepository: NoticeRepository,
    private val metaConfigProperties: MetaConfigProperties
) {

    @Cacheable(
        value = ["splashMetaData"],
        key = "#platform ?: 'ALL'",
        unless = "#result.maintenance != null || #result.notice != null"
    )
    fun getSplashMetaData(platform: String?): SplashMetaDataResponse {
        val platformEnum = platform?.let { 
            try {
                AppVersion.Platform.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                AppVersion.Platform.ALL
            }
        } ?: AppVersion.Platform.ALL

        return SplashMetaDataResponse(
            version = getVersionInfo(platformEnum),
            storeUrls = getStoreUrls(),
            serverHealth = getServerHealth(),
            maintenance = getMaintenanceInfo(),
            baseUrl = getBaseUrlInfo(),
            notice = getNoticeInfo(),
            policies = getPolicyUrls()
        )
    }

    private fun getVersionInfo(platform: AppVersion.Platform): VersionInfo {
        // Try to find platform-specific version first, then fall back to ALL
        val version = if (platform == AppVersion.Platform.ALL) {
            appVersionRepository.findFirstByIsActiveTrueAndPlatformOrderByCreatedAtDesc(AppVersion.Platform.ALL)
        } else {
            appVersionRepository.findFirstByIsActiveTrueAndPlatformInOrderByCreatedAtDesc(
                listOf(platform, AppVersion.Platform.ALL)
            )
        } ?: throw IllegalStateException("No active app version found for platform: $platform")

        return VersionInfo(
            currentVersion = version.version,
            forceUpdate = version.forceUpdate,
            minimumVersion = version.minimumVersion
        )
    }

    private fun getStoreUrls(): StoreUrls {
        return StoreUrls(
            appStore = metaConfigProperties.storeUrls.appStore,
            playStore = metaConfigProperties.storeUrls.playStore
        )
    }

    private fun getServerHealth(): ServerHealth {
        return ServerHealth(
            status = "HEALTHY",
            timestamp = LocalDateTime.now()
        )
    }

    private fun getMaintenanceInfo(): MaintenanceInfo? {
        val maintenance = maintenanceRepository.findActiveMaintenances().firstOrNull() ?: return null

        return MaintenanceInfo(
            title = maintenance.title,
            content = maintenance.content,
            startTime = maintenance.startTime,
            endTime = maintenance.endTime,
            isUnderMaintenance = maintenance.isUnderMaintenance()
        )
    }

    private fun getBaseUrlInfo(): BaseUrlInfo {
        return BaseUrlInfo(
            apiBaseUrl = metaConfigProperties.baseUrl.api
        )
    }

    private fun getNoticeInfo(): NoticeInfo? {
        val notices = noticeRepository.findActiveNotices()
        val topNotice = notices.firstOrNull() ?: return null

        return NoticeInfo(
            title = topNotice.title,
            content = topNotice.content,
            routerUrl = topNotice.routerUrl,
            noticeType = topNotice.noticeType.name,
            priority = topNotice.priority,
            displayStartTime = topNotice.displayStartTime,
            displayEndTime = topNotice.displayEndTime
        )
    }

    private fun getPolicyUrls(): PolicyUrls {
        return PolicyUrls(
            termsOfService = metaConfigProperties.policies.termsOfService,
            privacyPolicy = metaConfigProperties.policies.privacyPolicy
        )
    }
}
