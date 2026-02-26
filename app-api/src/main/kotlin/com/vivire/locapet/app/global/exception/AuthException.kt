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

class PermanentlyBannedException(message: String = "영구 정지된 회원입니다.") :
    AuthException("MEMBER_004", message)

class RejoinCooldownException(message: String = "재가입 대기 기간입니다.") :
    AuthException("MEMBER_005", message)

class IdentityVerificationFailedException(message: String = "본인인증에 실패했습니다.") :
    AuthException("IDENTITY_001", message)

class OnboardingSessionExpiredException(message: String = "세션이 만료되었습니다. 소셜 로그인부터 다시 시도해주세요.") :
    AuthException("ONBOARDING_001", message)

class OnboardingStageInvalidException(message: String = "잘못된 온보딩 단계입니다.") :
    AuthException("ONBOARDING_002", message)
