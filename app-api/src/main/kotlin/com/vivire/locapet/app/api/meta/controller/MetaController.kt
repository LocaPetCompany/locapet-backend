package com.vivire.locapet.app.api.meta.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Meta API", description = "메타 데이터 관련 API")
@RestController
@RequestMapping("/api/v1/meta")
class MetaController {

    @Operation(summary = "메타데이터", description = "스플래쉬화면 메타 데이터 API")
    @PostMapping("/init")
    fun signUp() {

    }
}