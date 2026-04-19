# 03. 공통 정책 (Common Policies)

> 본 문서는 도메인을 가로지르는 **공통 정책**을 정의한다. API·DB·운영 레이어에 공통 적용되는 규칙으로, 도메인별 기획 문서 작성 시 이 문서를 **상위 규칙**으로 따른다.

---

## 1. 권한(Access) 정책

### 1.1 사용자 상태별 접근 권한

| 상태 | AccountStatus | OnboardingStage | 접근 가능 영역 | 비고 |
|------|---------------|-----------------|----------------|------|
| 비로그인 | - | - | 메타(공지/버전), 업체 상세 조회(일부)❓, 로그인 플로우 | 리뷰 작성/찜/문의 불가 |
| 온보딩 중 (본인인증 전) | (미생성) | `IDENTITY_REQUIRED` (가상) | 본인인증 API만 | `onboardingToken` 필수 |
| 온보딩 중 (프로필 대기) | `ACTIVE` | `PROFILE_REQUIRED` | 프로필 완성 API만 | `onboardingAccessToken` 필수 |
| 정상 로그인 | `ACTIVE` | `COMPLETED` | 전체 API | accessToken 필수 |
| 탈퇴 유예 중 | `WITHDRAW_REQUESTED` | `COMPLETED` | 로그인 시 자동 취소 후 `ACTIVE` | 별도 진입 차단 없음 |
| 탈퇴 완료 | `WITHDRAWN` | - | 차단 | 재로그인 시도 시 Rejoin Cooldown 검사 |
| 강제 탈퇴 | `FORCE_WITHDRAWN` | - | 차단 (영구) | `PermanentlyBannedException` |
| 관리자 | `ACTIVE` + `role=ADMIN` | `COMPLETED` | app-api 전체 + admin-api | admin-api는 별도 인증 (❓정책 미정) |

### 1.2 리소스 소유권 (Ownership) 규칙

- **리뷰 / 찜 / 반려동물 / 저장된 필터 / 문의** 는 본인만 수정·삭제 가능.
- 예외: 관리자는 admin-api를 통해 모든 리소스에 강제 조치 가능 (신고·제재 맥락).
- 위반 시 `FORBIDDEN` 응답.

### 1.3 읽기 공개 원칙

- 업체(Place), 리뷰(공개 상태), 공지, FAQ 는 **비로그인 조회 허용** 기본.
- 단, 맞춤리뷰 필터 적용은 로그인 필수.
- ❓ 업체 상세에서 전화번호·위치 좌표 노출 범위 정책 미정.

---

## 2. 데이터 보관 · 삭제 정책

### 2.1 탈퇴 시 데이터 처리

탈퇴는 2단계: `WITHDRAW_REQUESTED` (30일 유예) → `WITHDRAWN`.

| 데이터 | 유예 기간 중 | 탈퇴 완료 시 | 비고 |
|--------|------------|-------------|------|
| `Member` 레코드 | 유지 | 유지 (상태만 변경) | CI 해시 보존 (Identity Lock 정책용) |
| 개인정보 필드 (이름, 생년월일, 전화번호, 이메일, 프로필 이미지) | 유지 | **마스킹/NULL 처리** ❓ | 법적 보관 의무 검토 필요 |
| `SocialAccount` | 유지 | 삭제 | 재로그인 차단 |
| `Pet` | 유지 | **삭제** 제안 | 맞춤리뷰 무결성과 충돌하지 않음 (스냅샷) |
| `Favorite` | 유지 | 삭제 | 단순 교차 데이터 |
| `SavedFilter` | 유지 | 삭제 | |
| `Review` | 유지 | **유지 + 작성자 익명화** 제안 | 업체 리뷰 수/평점 보존 |
| `Inquiry` | 유지 | 유지 (익명화) | 운영 추적용 |
| `IdentityVerification` 로그 | 유지 | 유지 | 감사 로그 |
| `IdentityLock` | 유지 | 정책에 따라 유지 | 재가입 Cooldown 적용 |

> **결정 필요**: 리뷰 익명화 시 닉네임을 "탈퇴한 회원"으로 표시할지, 삭제할지. (V2 `탈퇴한회원_a3f9c2` 난수 suffix 제안)

### 2.2 이미지 삭제 정책

- 원본 이미지는 **soft delete** 후 배치로 물리 삭제. ❓주기 미정 (30일 제안).
- 리뷰 삭제 시 `ReviewImage` 도 soft delete 후 후속 배치 정리.

### 2.3 로그 보관 기간

- 감사 로그 (`IdentityVerification`, `Inquiry`, 관리자 조치 로그): **최소 3년** 제안. ❓법적 근거 확인 필요.
- 액세스 로그 / 에러 로그: 90일 (인프라 레벨).

---

## 3. 컨텐츠 정책

### 3.1 리뷰 작성 제약

- 같은 업체에 대해 1회원 1리뷰 원칙 (수정은 가능). ❓재작성 허용 여부 결정 필요.
- 본문 최소 10자, 최대 2000자 (제안).
- 사진 최대 10장 (제안).
- 금칙어/개인정보 패턴 필터링 (전화번호·주민번호 패턴). ❓필터 수준 결정 필요.

### 3.2 신고 흐름

```
1. 유저: 리뷰 상세 → [신고하기] 선택 → 사유 선택 → 제출
     → Report(targetType=REVIEW, targetId, reason) 생성
2. 관리자: admin-api 문의/신고 대시보드에서 검토
3. 처리:
   - 유효 신고: Review.status = HIDDEN, 작성자에게 알림(선택)
   - 반복 위반: Member 제재 정책 적용 (경고 → 일시정지 → 강제탈퇴)
4. 이력: ReportAction 로그 저장
```

- 같은 대상/사유로의 중복 신고는 동일 신고자 기준 차단.
- 신고 누적 기준 자동 숨김 정책 ❓ (예: 3건 이상 자동 HIDDEN 후 검토).

### 3.3 업체 게시 상태

| 상태 | 의미 | 유저 노출 |
|------|------|-----------|
| `DRAFT` | 관리자 작성 중 | 미노출 |
| `PUBLISHED` | 정상 노출 | 노출 |
| `HIDDEN` | 일시 숨김 (운영/법적 이슈) | 미노출, 기존 즐겨찾기/리뷰는 유지 |
| `CLOSED` | 폐업 | 상세 진입 가능하나 "폐업" 뱃지 + 찜 불가 ❓ |

---

## 4. 페이지네이션 · 정렬 공통 규칙

### 4.1 페이지네이션

**기본 방식: Cursor 기반 페이지네이션** 제안 (리뷰·검색·찜 목록 등 대용량 목록).

```
GET /api/v1/places/{id}/reviews?cursor={lastId}&size=20
```

- `size` 기본 20, 최대 50.
- `cursor` 미지정 시 최신부터.
- 응답 포맷 (공통):

```json
{
  "items": [ ... ],
  "nextCursor": "eyJpZCI6...",
  "hasNext": true
}
```

- 고정 페이지 수가 필요한 경우(공지사항 등)만 Offset 방식 사용 허용.
- Offset 방식 응답:

```json
{
  "items": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 142,
  "totalPages": 8
}
```

### 4.2 정렬 공통 규칙

- 기본 정렬: **최신순 (`createdAt DESC, id DESC`)**.
- 정렬 옵션은 `sort=latest | rating_high | rating_low | popular` 등 허용 값 열거.
- 2차 정렬은 항상 `id DESC` 포함 (Cursor 안정성 확보).

### 4.3 검색 파라미터 공통

- 키워드: `q=`
- 카테고리: `categoryId=`
- 좌표 기반: `lat=`, `lng=`, `radius=` (m 단위).
- 필터 조건은 가능한 한 GET query 로. 복잡 조합은 `POST /search` 바디 허용.

---

## 5. 이미지 업로드 정책

### 5.1 업로드 방식 ❓

**제안: S3 Presigned URL 기반 직접 업로드**

```
1. 클라이언트: POST /api/v1/uploads/presigned { domain: "REVIEW", fileName, contentType }
2. 서버: S3 presigned PUT URL 발급 + 업로드 메타 레코드 생성 (status=PENDING)
3. 클라이언트: S3로 직접 PUT
4. 클라이언트: POST /api/v1/reviews { ..., imageIds: [...] }
5. 서버: 업로드 확인 + status=COMMITTED
```

### 5.2 제약

| 항목 | 값 (제안) |
|------|----------|
| 허용 확장자 | `jpg`, `jpeg`, `png`, `webp`, `heic` |
| 최대 파일 크기 | 10MB / 장 |
| 최대 업로드 수 | 리뷰 10장, 반려동물 프로필 1장, 프로필 이미지 1장 |
| CDN | CloudFront (도메인 미정) ❓ |
| 이미지 리사이징 | 썸네일 (400x400), 중형 (800x800), 원본 — 서버 배치 or Lambda@Edge ❓ |

### 5.3 스토리지 구조 ❓

```
s3://locapet-media/
  ├── reviews/{reviewId}/{imageId}.{ext}
  ├── pets/{petId}/{imageId}.{ext}
  ├── members/{memberId}/profile.{ext}
  └── places/{placeId}/{imageId}.{ext}  (관리자 업로드)
```

### 5.4 보안

- Presigned URL TTL: 5분.
- 업로드 후 COMMIT 되지 않은 이미지는 배치로 정리 (24시간 후 삭제).
- EXIF 민감 정보 (GPS 등) 자동 제거 정책 필요 ❓.

---

## 6. 에러 응답 · 검증 공통 규칙

### 6.1 에러 응답 포맷 (현재 구현 기준)

```kotlin
// GlobalExceptionHandler.kt + ErrorResponse.kt
data class ErrorResponse(
    val errorCode: String,   // "AUTH_001", "VALIDATION_001" 등
    val message: String      // 유저 친화 메시지
)
```

### 6.2 에러 코드 네이밍 규칙

- 형식: `{DOMAIN}_{NNN}` (세 자리 숫자).
- 기존 코드 (확장 시 일관성 유지):
  - `AUTH_001~004`: 토큰·소셜 로그인
  - `MEMBER_001~005`: 회원
  - `IDENTITY_001`: 본인인증
  - `ONBOARDING_001~002`: 온보딩 세션
  - `VALIDATION_001`: 입력 검증
- 신규 도메인 제안:
  - `PET_001~`: 반려동물
  - `PLACE_001~`: 업체
  - `REVIEW_001~`: 리뷰
  - `FAVORITE_001~`: 찜
  - `SEARCH_001~`: 검색/필터
  - `REPORT_001~`: 신고
  - `INQUIRY_001~`: 문의
  - `UPLOAD_001~`: 이미지 업로드

### 6.3 HTTP 상태 코드 매핑 원칙

| 상태 | 사용 케이스 |
|------|-------------|
| `400` | 입력 검증 실패, 잘못된 요청 파라미터 |
| `401` | 토큰 없음/만료/무효 |
| `403` | 권한 부족, 탈퇴·정지 회원 |
| `404` | 리소스 없음 (업체·리뷰·회원 등) |
| `409` | 중복 (닉네임·찜 등) |
| `422` | 비즈니스 규칙 위반 (선택적 — MVP는 400 통합 권장) |
| `500` | 서버 오류 |

### 6.4 검증 공통 규칙

- Bean Validation (`@Valid`, `@NotBlank`, `@Size` 등) 사용.
- `MethodArgumentNotValidException` 을 `GlobalExceptionHandler` 에서 `VALIDATION_001` 로 통합 처리 (현재 구현).
- 여러 필드 에러는 `message` 에 `필드명: 메시지, ...` 로 결합.
- ❓ 향후 multi-error 응답 구조로 확장 검토 (`fieldErrors: [{field, message}]`).

### 6.5 I18N

- MVP: 한글 메시지만 제공.
- 향후 다국어 지원 시 `Accept-Language` 헤더 기반 분기 ❓.

---

## 7. 캐시 정책

### 7.1 Redis 캐시 기본값 (현재 구현)

- `CacheConfig.kt`: 기본 TTL **5분**, `GenericJacksonJsonRedisSerializer` 사용.
- 캐시 키 네임스페이스: `{cacheName}::{key}`.

### 7.2 도메인별 캐시 제안

| 캐시 | 키 | TTL | 무효화 트리거 |
|------|---|-----|--------------|
| `splashMetaData` | `platform` (현재 구현) | 5분 | 공지/유지보수/버전 변경 시 수동 flush ❓ |
| `placeDetail` | `placeId` | 10분 | 관리자 업체 수정 시 |
| `placeCategories` | 단일 키 | 1시간 | 관리자 카테고리 변경 시 |
| `homeFeed` | `memberId` or `anonymous` | 5분 | 섹션 변경 시 |
| `faqList` | 단일 키 | 1시간 | FAQ 변경 시 |

- 관리자 변경 시 **자동 캐시 무효화 전략** 정립 필요 ❓ (Redis PUB/SUB or 이벤트).

---

## 8. 시간·날짜 정책 (재확인)

- **모든 시각은 UTC 저장·전송** (`Instant` / `TIMESTAMPTZ`).
- 날짜(생년월일 등)만 `LocalDate`.
- API 응답은 ISO-8601 (`2026-04-18T09:30:00Z`).
- 클라이언트가 로컬 타임존 변환 담당 (Asia/Seoul 등).

---

## 9. 관리자(admin-api) 정책 ❓

- **현재 admin-api는 인증 로직이 명확히 구현되어 있지 않음.** (별도 문서화 필요)
- 제안: 별도 관리자 로그인 (ID/PW + 2FA) + IP 화이트리스트 + 관리자 Role 검사.
- 모든 관리자 조치는 감사 로그 필수.

---

## 10. 개인정보 보호 (PIPA 준수)

- 개인정보 수집 동의 (필수/선택) 온보딩에 포함됨 (`termsOfServiceAgreed`, `privacyPolicyAgreed`, `marketingConsent`).
- 개인정보 처리방침 URL: `MetaConfigProperties.policies.privacyPolicy` (application.yml 주입).
- 탈퇴 시 개인정보 파기 절차 명시 필요 (섹션 2.1 참조).
- 개인정보 파기 배치 주기·로그 ❓ 정의 필요.

---

## 11. 관련 문서

- [00. 서비스 개요](./00-overview.md)
- [01. 도메인 맵](./01-domain-map.md)
- [02. 용어집](./02-glossary.md)
- [CLAUDE.md (프로젝트 컨텍스트)](../../.claude/CLAUDE.md)
- 기존 구현 참고:
  - `app-api/src/main/kotlin/com/vivire/locapet/app/global/exception/GlobalExceptionHandler.kt`
  - `app-api/src/main/kotlin/com/vivire/locapet/app/global/exception/AuthException.kt`
  - `app-api/src/main/kotlin/com/vivire/locapet/app/global/security/SecurityConfig.kt`
