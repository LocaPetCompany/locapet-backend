package com.vivire.locapet.app.api.auth.controller

import com.vivire.locapet.app.api.auth.dto.*
import com.vivire.locapet.app.api.auth.service.AuthService
import com.vivire.locapet.domain.member.SocialProvider
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@Tag(name = "Auth API", description = "인증 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {

    @Operation(summary = "소셜 로그인", description = "소셜 로그인으로 기존/신규 사용자 분기")
    @PostMapping("/social/{provider}")
    fun socialLogin(
        @PathVariable provider: SocialProvider,
        @Valid @RequestBody request: SocialLoginRequest
    ): ResponseEntity<SocialLoginResponse> {
        val response = authService.socialLogin(provider, request)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "인증 토큰 재발급", description = "refreshToken으로 accessToken 재발급 + 상태 반환")
    @PostMapping("/reissue")
    fun reissue(@Valid @RequestBody request: ReissueRequest): ResponseEntity<ReissueResponse> {
        val response = authService.reissue(request)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "세션 상태 조회", description = "현재 사용자의 세션 상태 조회 (클라이언트 라우팅용)")
    @GetMapping("/session")
    fun getSession(@AuthenticationPrincipal memberId: Long): ResponseEntity<SessionResponse> {
        val response = authService.getSession(memberId)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "로그아웃", description = "로그아웃")
    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal memberId: Long): ResponseEntity<Void> {
        authService.logout(memberId)
        return ResponseEntity.noContent().build()
    }
}
