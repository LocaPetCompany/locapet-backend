package com.vivire.locapet.app.api.meta.controller

import com.vivire.locapet.app.api.meta.dto.SplashMetaDataResponse
import com.vivire.locapet.app.api.meta.service.MetaService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Meta API", description = "메타 데이터 관련 API")
@RestController
@RequestMapping("/api/v1/meta")
class MetaController(
    private val metaService: MetaService
) {

    @Operation(summary = "메타데이터", description = "스플래쉬화면 메타 데이터 API")
    @GetMapping("/init")
    fun getSplashMetaData(
        @Parameter(description = "플랫폼 (ios, android)", required = false)
        @RequestParam(required = false) platform: String?
    ): ResponseEntity<SplashMetaDataResponse> {
        val response = metaService.getSplashMetaData(platform)
        return ResponseEntity.ok(response)
    }
}