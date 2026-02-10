package com.vivire.locapet.app.api.auth.controller

import com.vivire.locapet.app.api.auth.dto.*
import com.vivire.locapet.app.api.auth.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth API", description = "인증 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {

    @Operation(summary = "로그인", description = "소셜 로그인")
    @PostMapping("/sign-in")
    fun signIn(@Valid @RequestBody request: SignInRequest): ResponseEntity<SignInResponse> {
        val response = authService.signIn(request)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "인증 토큰 재발급", description = "인증토큰 재발급")
    @PostMapping("/reissue")
    fun reissue(@Valid @RequestBody request: ReissueRequest): ResponseEntity<ReissueResponse> {
        val response = authService.reissue(request)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "로그아웃", description = "로그아웃")
    @PostMapping("/logout")
    fun logout(@AuthenticationPrincipal memberId: Long): ResponseEntity<Void> {
        authService.logout(memberId)
        return ResponseEntity.noContent().build()
    }
}
