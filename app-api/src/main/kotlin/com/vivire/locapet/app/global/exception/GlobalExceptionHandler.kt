package com.vivire.locapet.app.global.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(InvalidTokenException::class, ExpiredTokenException::class)
    fun handleTokenException(e: AuthException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(e.errorCode, e.message ?: "인증 오류"))
    }

    @ExceptionHandler(InvalidRefreshTokenException::class)
    fun handleInvalidRefreshToken(e: AuthException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(e.errorCode, e.message ?: "리프레시 토큰 오류"))
    }

    @ExceptionHandler(SocialLoginFailedException::class)
    fun handleSocialLoginFailed(e: AuthException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(e.errorCode, e.message ?: "소셜 로그인 실패"))
    }

    @ExceptionHandler(MemberNotFoundException::class)
    fun handleMemberNotFound(e: AuthException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(e.errorCode, e.message ?: "회원 없음"))
    }

    @ExceptionHandler(DuplicateNicknameException::class)
    fun handleDuplicateNickname(e: AuthException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(e.errorCode, e.message ?: "닉네임 중복"))
    }

    @ExceptionHandler(MemberWithdrawnException::class)
    fun handleMemberWithdrawn(e: AuthException): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(e.errorCode, e.message ?: "탈퇴 회원"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = e.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("VALIDATION_001", message))
    }
}
