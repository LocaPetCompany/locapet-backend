package com.vivire.locapet.app.api.meta.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "스플래시 메타데이터 응답")
data class SplashMetaDataResponse(
    @Schema(description = "앱 버전 정보")
    val version: VersionInfo,

    @Schema(description = "스토어 URL")
    val storeUrls: StoreUrls,

    @Schema(description = "서버 상태")
    val serverHealth: ServerHealth,

    @Schema(description = "점검 정보", nullable = true)
    val maintenance: MaintenanceInfo?,

    @Schema(description = "Base URL")
    val baseUrl: BaseUrlInfo,

    @Schema(description = "공지사항", nullable = true)
    val notice: NoticeInfo?,

    @Schema(description = "정책 URL")
    val policies: PolicyUrls
)

@Schema(description = "앱 버전 정보")
data class VersionInfo(
    @Schema(description = "현재 버전", example = "1.0.0")
    val currentVersion: String,

    @Schema(description = "강제 업데이트 여부")
    val forceUpdate: Boolean,

    @Schema(description = "최소 지원 버전", nullable = true, example = "0.9.0")
    val minimumVersion: String?
)

@Schema(description = "스토어 URL")
data class StoreUrls(
    @Schema(description = "App Store URL")
    val appStore: String,

    @Schema(description = "Play Store URL")
    val playStore: String
)

@Schema(description = "서버 상태")
data class ServerHealth(
    @Schema(description = "상태", example = "HEALTHY")
    val status: String,

    @Schema(description = "타임스탬프")
    val timestamp: LocalDateTime
)

@Schema(description = "점검 정보")
data class MaintenanceInfo(
    @Schema(description = "점검 제목")
    val title: String,

    @Schema(description = "점검 내용")
    val content: String,

    @Schema(description = "점검 시작 시간")
    val startTime: LocalDateTime,

    @Schema(description = "점검 종료 시간")
    val endTime: LocalDateTime,

    @Schema(description = "점검 중 여부")
    val isUnderMaintenance: Boolean
)

@Schema(description = "Base URL 정보")
data class BaseUrlInfo(
    @Schema(description = "API Base URL")
    val apiBaseUrl: String
)

@Schema(description = "공지사항 정보")
data class NoticeInfo(
    @Schema(description = "공지 제목")
    val title: String,

    @Schema(description = "공지 내용")
    val content: String,

    @Schema(description = "라우터 URL", nullable = true)
    val routerUrl: String?,

    @Schema(description = "공지 타입", example = "INFO")
    val noticeType: String,

    @Schema(description = "우선순위", example = "5")
    val priority: Int,

    @Schema(description = "노출 시작 시간")
    val displayStartTime: LocalDateTime,

    @Schema(description = "노출 종료 시간")
    val displayEndTime: LocalDateTime
)

@Schema(description = "정책 URL")
data class PolicyUrls(
    @Schema(description = "이용약관 URL")
    val termsOfService: String,

    @Schema(description = "개인정보처리방침 URL")
    val privacyPolicy: String
)
