package com.vivire.locapet.app.api.member.controller

import com.vivire.locapet.app.api.member.service.MemberService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Member API", description = "회원 관련 API")
@RestController
@RequestMapping("/api/v1/member")
class MemberController(
    private val memberService: MemberService
) {

    @Operation(summary = "탈퇴 신청", description = "일반탈퇴 요청 (30일 유예 기간)")
    @PostMapping("/withdraw")
    fun withdraw(@AuthenticationPrincipal memberId: Long): ResponseEntity<Void> {
        memberService.requestWithdrawal(memberId)
        return ResponseEntity.noContent().build()
    }
}
