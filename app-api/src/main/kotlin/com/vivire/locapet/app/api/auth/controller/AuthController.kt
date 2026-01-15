package com.vivire.locapet.app.api.auth.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth API", description = "인증 관련 API")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    @Operation(summary = "로그인", description = "소셜 로그인")
    @PostMapping("/sing-in")
    fun singIn() {

    }

    @Operation(summary = "인증 토큰 재발급", description = "인증토큰 재발급")
    @PostMapping("/reissue")
    fun reissue() {

    }

    @Operation(summary = "로그아웃", description = "로그아웃")
    @PostMapping("/logout")
    fun logout() {

    }


}