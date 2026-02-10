package com.vivire.locapet.app.api.member.controller

import com.vivire.locapet.app.api.member.dto.SignUpRequest
import com.vivire.locapet.app.api.member.dto.SignUpResponse
import com.vivire.locapet.app.api.member.service.MemberService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Member API", description = "회원 관련 API")
@RestController
@RequestMapping("/api/v1/member")
class MemberController(
    private val memberService: MemberService
) {

    @Operation(summary = "회원가입", description = "소셜 로그인을 통해 회원가입을 진행합니다.")
    @PostMapping("/sign-up")
    fun signUp(@Valid @RequestBody request: SignUpRequest): ResponseEntity<SignUpResponse> {
        val response = memberService.signUp(request)
        return ResponseEntity.ok(response)
    }
}
