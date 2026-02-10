package com.vivire.locapet.app.global.exception

sealed class AuthException(val errorCode: String, message: String) : RuntimeException(message)

class InvalidTokenException(message: String = "유효하지 않은 토큰입니다.") :
    AuthException("AUTH_001", message)

class ExpiredTokenException(message: String = "만료된 토큰입니다.") :
    AuthException("AUTH_002", message)

class SocialLoginFailedException(message: String = "소셜 로그인에 실패했습니다.") :
    AuthException("AUTH_003", message)

class InvalidRefreshTokenException(message: String = "유효하지 않은 리프레시 토큰입니다.") :
    AuthException("AUTH_004", message)

class MemberNotFoundException(message: String = "회원을 찾을 수 없습니다.") :
    AuthException("MEMBER_001", message)

class DuplicateNicknameException(message: String = "이미 사용 중인 닉네임입니다.") :
    AuthException("MEMBER_002", message)

class MemberWithdrawnException(message: String = "탈퇴한 회원입니다.") :
    AuthException("MEMBER_003", message)
