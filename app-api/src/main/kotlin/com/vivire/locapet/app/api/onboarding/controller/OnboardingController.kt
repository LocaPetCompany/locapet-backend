package com.vivire.locapet.app.api.onboarding.controller

import com.vivire.locapet.app.api.onboarding.dto.*
import com.vivire.locapet.app.api.onboarding.service.OnboardingService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Onboarding API", description = "온보딩 관련 API")
@RestController
@RequestMapping("/api/v1/onboarding")
class OnboardingController(
    private val onboardingService: OnboardingService
) {

    @Operation(summary = "본인인증", description = "PASS 본인인증 + CI 중복 검사")
    @PostMapping("/identity/verify")
    fun verifyIdentity(
        @Valid @RequestBody request: IdentityVerifyRequest
    ): ResponseEntity<IdentityVerifyResponse> {
        val response = onboardingService.verifyIdentity(request)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "프로필 완성", description = "닉네임/약관 동의 후 온보딩 완료")
    @PostMapping("/profile/complete")
    fun completeProfile(
        @Valid @RequestBody request: ProfileCompleteRequest
    ): ResponseEntity<ProfileCompleteResponse> {
        val response = onboardingService.completeProfile(request)
        return ResponseEntity.ok(response)
    }
}
