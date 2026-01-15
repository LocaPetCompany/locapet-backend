package com.vivire.locapet.app.api.member.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Member API", description = "회원 관련 API")
@RestController
@RequestMapping("/api/v1/member")
class MemberController {

    @Operation(summary = "회원가입", description = "소셜 로그인을 통해 회원가입을 진행합니다.")
    @PostMapping("/sign-up")
    fun signUp() {

    }

}