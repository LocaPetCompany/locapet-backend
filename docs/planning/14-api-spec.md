# 14. API 스펙 (Full)

> 본 문서는 Phase 1~4 기획서 14편을 바탕으로 **Phase 5 실제 구현용 REST API 전체 명세**를 정의한다.
> OpenAPI 수준의 상세도로 요청/응답 스키마, 에러 코드, 인증 요구를 명시한다.
> 상위 문서: [15. DB 스키마](./15-db-schema.md), [16. 구현 로드맵](./16-implementation-roadmap.md)

---

## 0. 공통 규약

### 0.1 Base URL

| 환경 | app-api | admin-api |
|---|---|---|
| local | `http://localhost:8080` | `http://localhost:8081` |
| dev | `https://dev-api.locapet.app` | `https://dev-admin-api.locapet.app` |
| staging | `https://staging-api.locapet.app` | `https://staging-admin-api.locapet.app` |
| prod | `https://api.locapet.app` | `https://admin-api.locapet.app` |

### 0.2 인증 체계

| 토큰 | 헤더 | 사용처 |
|---|---|---|
| none | - | 메타, 공지, FAQ, 약관, 업체 조회, 검색 |
| `onboardingToken` | Request Body 필드 | `POST /api/v1/onboarding/identity/verify` 만 |
| `onboardingAccessToken` | `Authorization: Bearer {token}` | `POST /api/v1/onboarding/profile/complete` 만 |
| `accessToken` | `Authorization: Bearer {token}` | 인증된 모든 회원 API |
| adminToken (`Authorization: Bearer {token}`) | - | admin-api 전체. ID/PW 로그인 + IP 화이트리스트 |

### 0.3 공통 헤더

```
Content-Type: application/json; charset=UTF-8
Accept: application/json
Authorization: Bearer {accessToken}    (필요 시)
X-Client-Platform: IOS | ANDROID       (선택 — 로깅/분석)
X-Client-Version: 1.2.3                (선택)
```

### 0.4 에러 응답 포맷

기존 구현 (`app-api/src/main/kotlin/com/vivire/locapet/app/global/exception/ErrorResponse.kt`) 을 **확장**한다.

```json
{
  "errorCode": "REVIEW_002",
  "message": "이미 이 업체에 리뷰를 작성하셨어요.",
  "details": {                            // 선택, 필드별 validation 에러 등
    "field": "content",
    "reason": "MIN_LENGTH"
  }
}
```

- `errorCode` 형식: `{DOMAIN}_{NNN}` — 전역 고유.
- `details` 는 MVP 선택. 클라이언트가 필드별 에러 표시를 원할 때 제공.
- HTTP 상태 매핑: 400 (검증), 401 (인증), 403 (권한), 404 (없음), 409 (충돌), 429 (rate limit), 500 (서버).

### 0.5 Cursor 페이지네이션

기본 공통 포맷 — 모든 대용량 목록에 적용 ([03-common-policies.md §4](./03-common-policies.md)).

**요청**:
```
GET /api/v1/...?cursor={opaqueString}&size=20
```
- `size`: 기본 20, 최대 50.
- `cursor`: 미지정 시 최신부터.

**응답**:
```json
{
  "items": [ ... ],
  "nextCursor": "eyJpZCI6MTAwMCwiY3JlYXRlZEF0IjoiMjAyNi0wNC0xOFQwOTozMFoifQ==",
  "hasNext": true
}
```

- `nextCursor` 는 Base64(JSON) — `{id, createdAt}` 등. 서버가 불투명 문자열로 취급하도록 가이드.
- 예외: FAQ, 약관, 공지(PINNED 포함) 는 소량이므로 offset 기반 허용.

### 0.6 시간 포맷

- 모든 시각: ISO-8601 UTC, 예: `2026-04-18T09:30:00Z`.
- 생년월일: `YYYY-MM-DD` (`LocalDate`).

### 0.7 관련 화면 Figma ID

각 API 섹션에서 관련 화면 노드 ID 를 3개 이내로 첨부. 상세는 [12-ux-flows.md](./12-ux-flows.md), [13-screen-api-map.md](./13-screen-api-map.md) 참조.

---

## 1. Auth / Onboarding

### 1.1 `POST /api/v1/auth/social/{provider}` — 소셜 로그인

- **인증**: none
- **설명**: 소셜 액세스 토큰으로 로그인. 기존/신규/탈퇴유예 분기별 다른 응답 type 반환.
- **Path Param**: `provider` ∈ `kakao | naver | google | apple`
- **Request**:
  ```json
  {
    "socialToken": "eyJhbGciOi...",
    "deviceToken": "fcm_or_apns_token",
    "platform": "IOS"
  }
  ```
- **Response 200**: 4가지 분기
  - `type=LOGIN` — 정상 로그인 (ACTIVE+COMPLETED, WITHDRAW_REQUESTED 자동취소 포함)
    ```json
    {
      "type": "LOGIN",
      "accessToken": "...",
      "refreshToken": "...",
      "member": { "id": 7, "nickname": "초코맘", "profileImageUrl": "...", "onboardingStage": "COMPLETED" },
      "pendingTermAgreements": []
    }
    ```
  - `type=PROFILE_REQUIRED` — 본인인증 완료, 프로필 미완료
    ```json
    { "type": "PROFILE_REQUIRED", "onboardingAccessToken": "...", "memberId": 7 }
    ```
  - `type=IDENTITY_REQUIRED` — 신규 유저
    ```json
    { "type": "IDENTITY_REQUIRED", "onboardingToken": "01HX-..." }
    ```
  - `type=FORCE_WITHDRAWN` — 영구 차단
    ```json
    { "type": "FORCE_WITHDRAWN" }
    ```
- **에러**: `AUTH_001` (소셜 토큰 무효, 400), `AUTH_003` (재가입 대기, 403)
- **화면**: 로그인 진입

### 1.2 `POST /api/v1/auth/reissue` — 토큰 재발급

- **인증**: none (refreshToken in body)
- **Request**: `{ "refreshToken": "..." }`
- **Response 200**: `{ "accessToken": "...", "refreshToken": "...", "pendingTermAgreements": [...] }`
- **에러**: `AUTH_002` (refresh 만료/무효, 401)

### 1.3 `GET /api/v1/auth/session` — 세션 조회 (라우팅용)

- **인증**: accessToken
- **Response 200**:
  ```json
  {
    "memberId": 7,
    "accountStatus": "ACTIVE",
    "onboardingStage": "COMPLETED",
    "nickname": "초코맘",
    "pendingTermAgreements": []
  }
  ```

### 1.4 `POST /api/v1/auth/logout`

- **인증**: accessToken
- **Request**: `{}` (body 없음 허용)
- **Response 204**: No Content. 서버는 refreshToken 파기 + 해당 device_token 비활성화.

### 1.5 `POST /api/v1/onboarding/identity/verify` — 본인인증

- **인증**: none (onboardingToken in body — 1회용)
- **Request**:
  ```json
  {
    "onboardingToken": "01HX-...",
    "passTransactionId": "PASS-ABCDE",
    "provider": "KAKAO"
  }
  ```
- **Response 200**:
  ```json
  {
    "onboardingAccessToken": "...",
    "member": { "id": 8, "onboardingStage": "PROFILE_REQUIRED" }
  }
  ```
- **에러**: `ONBOARDING_001` (세션 만료, 400), `IDENTITY_001` (PASS 검증 실패, 400), `AUTH_003` (CI 잠금, 403)

### 1.6 `POST /api/v1/onboarding/profile/complete` — 프로필 완성

- **인증**: onboardingAccessToken
- **Request**:
  ```json
  {
    "nickname": "초코맘",
    "termAgreements": [
      { "termId": 3, "agreed": true },
      { "termId": 4, "agreed": true },
      { "termId": 5, "agreed": true },
      { "termId": 6, "agreed": false }
    ]
  }
  ```
- **Response 200**:
  ```json
  {
    "accessToken": "...",
    "refreshToken": "...",
    "member": { "id": 8, "nickname": "초코맘", "onboardingStage": "COMPLETED" }
  }
  ```
- **에러**: `MEMBER_003` (닉네임 중복, 409), `TERMS_001` (필수 약관 누락, 400)

---

## 2. Member

### 2.1 `GET /api/v1/members/me` — 내 프로필 조회

- **인증**: accessToken
- **Response 200**:
  ```json
  {
    "id": 7,
    "nickname": "초코맘",
    "email": "choco@example.com",
    "profileImageUrl": "https://cdn.locapet.app/members/7/profile.jpg",
    "accountStatus": "ACTIVE",
    "onboardingStage": "COMPLETED",
    "marketingConsent": true,
    "createdAt": "2026-01-10T02:00:00Z"
  }
  ```

### 2.2 `PATCH /api/v1/members/me` — 프로필 수정

- **인증**: accessToken
- **Request (부분 업데이트)**:
  ```json
  { "nickname": "초코아빠", "profileImageId": "img_01HX..." }
  ```
- **Response 200**: 갱신된 회원 객체 (§2.1 과 동일).
- **에러**: `MEMBER_003` (닉네임 중복, 409), `VALIDATION_001` (400), `UPLOAD_002` (이미지 커밋 안됨, 400)

### 2.3 `GET /api/v1/members/me/nickname/check?nickname=초코맘` — 중복 검사

- **인증**: accessToken (onboarding 중엔 onboardingAccessToken 도 허용)
- **Response 200**: `{ "available": true }`
- **제약**: 닉네임 2~20자, 이모지 허용 여부는 공통 정책 따름.

### 2.4 `POST /api/v1/members/me/withdraw` — 탈퇴 신청

- **인증**: accessToken
- **Request**: `{ "reason": "SERVICE_UNUSED" }` (선택)
- **Response 200**: `{ "effectiveAt": "2026-05-18T09:30:00Z" }` (30일 후)

### 2.5 `POST /api/v1/members/me/logout`

§1.4 의 `/api/v1/auth/logout` 별칭. 둘 다 구현 권장 (기존 코드 관성 + 규약).

---

## 3. Pet

### 3.1 `GET /api/v1/pets` — 내 반려동물 목록

- **인증**: accessToken
- **Response 200**:
  ```json
  {
    "items": [
      {
        "id": 12, "name": "초코", "species": "DOG", "breed": "포메라니안",
        "gender": "MALE", "birthDate": "2022-05-01", "isBirthDateEstimated": false,
        "weightKg": 3.2, "size": "SMALL", "isNeutered": true,
        "personalityTags": ["활발","친화적"],
        "profileImageUrl": "https://cdn.locapet.app/pets/12/img_...jpg",
        "isPrimary": true,
        "createdAt": "2026-04-18T09:30:00Z",
        "updatedAt": "2026-04-18T09:30:00Z"
      }
    ]
  }
  ```
- **정렬**: `isPrimary DESC, created_at ASC, id ASC`.

### 3.2 `POST /api/v1/pets` — 등록

- **인증**: accessToken
- **Request**:
  ```json
  {
    "name": "초코",
    "species": "DOG",
    "breed": "포메라니안",
    "gender": "MALE",
    "birthDate": "2022-05-01",
    "isBirthDateEstimated": false,
    "weightKg": 3.2,
    "size": "SMALL",
    "isNeutered": true,
    "personalityTags": ["활발","친화적"],
    "profileImageId": "img_01HX..."
  }
  ```
- **Response 201**: PetResponse (§3.1 단일 아이템).
- **에러**: `VALIDATION_001` (400), `PET_002` (species 무효, 400), `PET_004` (10마리 초과, 409), `UPLOAD_002` (400)
- **규칙**: 첫 등록이면 자동 `isPrimary=true`.

### 3.3 `GET /api/v1/pets/{id}`

- **인증**: accessToken
- **Response 200**: PetResponse
- **에러**: `PET_001` (404), `PET_005` (타인 소유, 403)

### 3.4 `PATCH /api/v1/pets/{id}` — 수정 (species 불가)

- **인증**: accessToken
- **Request**: 수정 가능 필드 (species 제외).
- **Response 200**: PetResponse
- **에러**: `PET_001`, `PET_003` (species 변경 시도, 400), `PET_005`, `PET_007` (삭제된 Pet, 400)

### 3.5 `DELETE /api/v1/pets/{id}` — 소프트 삭제

- **인증**: accessToken
- **Response 204**: 대표 삭제 시 자동 승격 (`created_at ASC` 기준 다음 Pet).
- **에러**: `PET_001`, `PET_005`

### 3.6 `POST /api/v1/pets/{id}/primary` — 대표 지정

- **인증**: accessToken
- **Response 200**: `{ "petId": 12, "isPrimary": true }`
- **에러**: `PET_001`, `PET_005`, `PET_006` (동시성 충돌, 400)

---

## 4. Place (조회 + 업체 신고)

### 4.1 `GET /api/v1/places/search` — 키워드 + 필터 검색

- **인증**: optional (로그인 시 `isFavorited` 포함)
- **Query Parameters**:
  | 필드 | 타입 | 필수 | 설명 |
  |---|---|---|---|
  | `q` | string | N | 키워드 (1~50자) |
  | `categories` | CSV | N | `RESTAURANT,CAFE,KINDERGARTEN,LODGING,PARK` |
  | `species` | CSV | N | `DOG,CAT` |
  | `sizes` | CSV | N | `SMALL,MEDIUM,LARGE` |
  | `amenities` | CSV | N | **OR 매칭** (Phase 5 확정 변경) |
  | `indoorAllowed` | boolean | N | |
  | `cageFree` | boolean | N | |
  | `minRating` | number | N | 0.0~5.0 |
  | `openNow` | boolean | N | KST 기준 |
  | `lat`, `lng` | number | N | 좌표 (`sort=distance` 시 필수) |
  | `radius` | int | N | 100~20000 (m), 기본 3000 |
  | `sort` | string | N | `distance | latest | rating | popular` |
  | `cursor`, `size` | - | N | 페이지네이션 |
  | `savedFilterId` | long | N | 저장된 필터 적용 |
- **Response 200**:
  ```json
  {
    "items": [
      {
        "id": 42,
        "name": "강아지와 식당",
        "category": "RESTAURANT",
        "address": "서울시 강남구 ...",
        "location": { "lat": 37.5013, "lng": 127.0394 },
        "distanceMeters": 321,
        "thumbnailImageUrl": "...",
        "ratingAvg": 4.6,
        "reviewCount": 128,
        "favoriteCount": 342,
        "isFavorited": true,
        "isClosed": false
      }
    ],
    "nextCursor": "...",
    "hasNext": true
  }
  ```
- **에러**: `PLACE_002` (400, 좌표 반쪽만), `PLACE_005` (400, distance 정렬인데 좌표 없음), `SEARCH_004` (403, 타인 savedFilter)

### 4.2 `GET /api/v1/places/near` — 반경 검색 별칭

- 동일 검색 API의 alias. `sort=distance` 강제 + `lat`, `lng` 필수.

### 4.3 `GET /api/v1/places/recommended` — 로카 추천

- **인증**: optional
- **Query**: `size=10` (기본)
- **Response 200**: PlaceListItem 배열 + 큐레이션 메타(title, description).
- **Source**: `place_curations` 테이블 + 활성 큐레이션만 (`visible_from <= NOW < visible_until`).

### 4.4 `GET /api/v1/places/trending` — 요즘 찜 많은

- **인증**: optional
- **Query**: `window=7d` (고정), `size=20`
- **Response 200**: PlaceListItem 배열.
- **Source**: Redis ZSet `wishlist:trending:7d` + DB 상세 조회. Redis 실패 시 **빈 배열 200** (graceful).

### 4.5 `GET /api/v1/places/{id}` — 상세

- **인증**: optional
- **Response 200**:
  ```json
  {
    "id": 42,
    "name": "강아지와 식당",
    "category": "RESTAURANT",
    "status": "PUBLISHED",
    "address": "서울시 강남구 ...",
    "roadAddress": "...",
    "regionCode": "11680",
    "location": { "lat": 37.5013, "lng": 127.0394 },
    "phone": "02-****-5678",
    "website": "https://...",
    "kakaoChannelId": "_abcdef",
    "email": "hello@example.com",
    "description": "...",
    "thumbnailImageUrl": "...",
    "images": [ { "id": 101, "imageUrl": "...", "displayOrder": 0 } ],
    "hours": [
      { "dayOfWeek": 1, "openTime": "11:00", "closeTime": "22:00",
        "isClosed": false, "isNextDay": false, "note": null }
    ],
    "amenities": ["PARKING","TERRACE","WATER_BOWL"],
    "petPolicies": [
      { "species": "DOG", "maxSize": "MEDIUM", "requiresCage": false,
        "indoorAllowed": true, "extraFeeKrw": 0, "notes": null }
    ],
    "categoryMeta": { "cuisineType": "한식", "hasKidsMenu": false },
    "ratingAvg": 4.6,
    "reviewCount": 128,
    "favoriteCount": 342,
    "isFavorited": true,
    "isClosed": false,
    "createdAt": "2026-01-10T02:00:00Z",
    "updatedAt": "2026-04-10T05:30:00Z"
  }
  ```
- **비로그인 마스킹**: `phone` → `02-****-5678` 형태, 좌표는 그대로 공개 (지도 필수이므로).
- **에러**: `PLACE_001` (404, 없음/HIDDEN/DRAFT)

### 4.6 `GET /api/v1/places/{id}/reviews` — 업체 리뷰 목록

§5.4 참조 (Review 섹션).

### 4.7 `POST /api/v1/places/{id}/favorite` / `DELETE` — 찜 토글

§6 Wishlist 섹션 참조.

### 4.8 `POST /api/v1/places/{id}/report` — 업체 신고 (Phase 5 확정 변경)

- **인증**: accessToken
- **Request**:
  ```json
  {
    "reasonCode": "INFO_ERROR",
    "memo": "영업시간이 다릅니다."
  }
  ```
- **Response 202 Accepted**: `{ "reportId": 1001 }`
- **reasonCode Enum**: `INFO_ERROR | CLOSED_REPORT | INAPPROPRIATE | ETC`
- **구현**: 내부적으로는 Inquiry 또는 `place_reports` 테이블. MVP 는 `inquiries (category=PLACE_INFO_ERROR, related_place_id=:id)` 로 생성하는 경로 재사용 가능 (스키마 확장 필요 시 Phase 6).
- **에러**: `PLACE_001` (404)

---

## 5. Review

### 5.1 `POST /api/v1/reviews` — 작성 (hasPet 분기)

- **인증**: accessToken
- **Request (hasPet=true)**:
  ```json
  {
    "placeId": 42,
    "rating": 5,
    "content": "...",
    "visitDate": "2026-04-15",
    "hasPet": true,
    "petIds": [12, 13],
    "imageIds": ["img_01HX...","img_01HY..."]
  }
  ```
- **Request (hasPet=false, Phase 5 확정 변경)**:
  ```json
  {
    "placeId": 42,
    "rating": 4,
    "content": "친구 반려견과 동행하였는데 분위기가 좋았어요.",
    "visitDate": "2026-04-15",
    "hasPet": false,
    "imageIds": []
  }
  ```
- **검증 규칙**:
  - `hasPet=true` → `petIds` **1~5개 필수**, 본인 소유 + 활성
  - `hasPet=false` → `petIds` **금지** (있으면 400)
- **Response 201**: ReviewResponse (§5.6)
- **에러**: `REVIEW_002` (중복, 409), `REVIEW_003` (400), `REVIEW_005` (폐업, 409), `REVIEW_007` (petIds 이상, 400), `REVIEW_009` (활성 Pet 0 + hasPet=true, 400)

### 5.2 `PATCH /api/v1/reviews/{id}` — 수정

- **인증**: accessToken (본인만)
- **Request**: 수정 가능 필드 (`placeId`, `memberId` 외 전부). `hasPet` 전환 가능.
- **Response 200**: ReviewResponse
- **에러**: `REVIEW_001`, `REVIEW_004` (본인 아님, 403)

### 5.3 `DELETE /api/v1/reviews/{id}` — 삭제 (소프트)

- **인증**: accessToken (본인 또는 관리자)
- **Response 204**
- **에러**: `REVIEW_001`, `REVIEW_004`

### 5.4 `GET /api/v1/places/{placeId}/reviews` — 업체 리뷰 목록

- **인증**: optional (`tailored` 정렬은 필수)
- **Query**:
  | 필드 | 타입 | 필수 | 설명 |
  |---|---|---|---|
  | `sort` | string | N | `latest | rating_high | rating_low | helpful | tailored` (5종, Phase 5) |
  | `cursor`, `size` | - | N | |
- **Response 200**: `{ items: [ReviewListItem], nextCursor, hasNext }`
- **에러**: `REVIEW_006` (대표 Pet 없이 tailored, 400), `PLACE_001` (404)

### 5.5 `GET /api/v1/reviews/{id}` — 상세

- **인증**: optional
- **Response 200**: ReviewResponse

### 5.6 Review 응답 스키마

```json
{
  "id": 1001,
  "placeId": 42,
  "placeName": "강아지와 식당",
  "rating": 5,
  "content": "...",
  "visitDate": "2026-04-15",
  "hasPet": true,
  "images": [ { "id": 9001, "imageUrl": "...", "displayOrder": 0 } ],
  "pets": [
    { "petId": 12, "name": "초코", "species": "DOG", "size": "SMALL", "breed": "포메라니안" }
  ],
  "author": { "memberId": 7, "nickname": "초코맘", "profileImageUrl": "..." },
  "likeCount": 34,
  "likedByMe": true,
  "status": "ACTIVE",
  "createdAt": "2026-04-16T08:20:00Z",
  "updatedAt": "2026-04-16T08:20:00Z"
}
```

`hasPet=false` 일 때는 `pets: []` 빈 배열. 클라이언트는 "반려동물 없이 방문" 뱃지 렌더.

### 5.7 `POST /api/v1/reviews/{id}/like` — 좋아요

- **인증**: accessToken
- **Response 200**: `{ "liked": true, "likeCount": 35 }`
- **에러**: `REVIEW_008` (본인 리뷰, 400), `REVIEW_001`

### 5.8 `DELETE /api/v1/reviews/{id}/like` — 좋아요 취소

- **인증**: accessToken
- **Response 200**: `{ "liked": false, "likeCount": 34 }` (idempotent)

### 5.9 `POST /api/v1/reviews/{id}/report` — 리뷰 신고

- **인증**: accessToken
- **Request**: `{ "reasonCode": "ABUSE", "memo": "욕설 포함" }`
- **Response 202**: `{ "reportId": 2001 }`
- **자동 HIDDEN 임계값**: **5건** (Phase 5 확정 변경, 기획서 3건 → 5건).
- **에러**: `REPORT_001` (중복 신고, 409)

### 5.10 `GET /api/v1/members/me/reviews` — 내 리뷰 목록

- **인증**: accessToken
- **Query**: `cursor`, `size`
- **Response 200**: ReviewListItem 배열 (ACTIVE + HIDDEN 포함. DELETED 제외).

---

## 6. Wishlist / Recently Viewed

### 6.1 `GET /api/v1/members/me/favorites` — 내 찜 목록

- **인증**: accessToken
- **Query**: `cursor`, `size=20`, `sort=latest` (기본)
- **Response 200**:
  ```json
  {
    "items": [
      {
        "placeId": 42, "name": "강아지와 식당", "category": "RESTAURANT",
        "address": "서울시 강남구 ...", "thumbnailImageUrl": "...",
        "ratingAvg": 4.6, "reviewCount": 128, "favoriteCount": 343,
        "favoritedAt": "2026-04-16T08:20:00Z",
        "isClosed": false
      }
    ],
    "nextCursor": "...", "hasNext": true
  }
  ```

### 6.2 `POST /api/v1/members/me/favorites/{placeId}` — 찜 추가

- **인증**: accessToken
- **Response 200**: `{ "placeId": 42, "isFavorited": true, "favoriteCount": 343 }`
- **Idempotent**: 기존 찜이면 200 그대로.
- **에러**: `PLACE_001` (404), `PLACE_004` (폐업, 409)

### 6.3 `DELETE /api/v1/members/me/favorites/{placeId}` — 찜 해제

- **인증**: accessToken
- **Response 200**: `{ "placeId": 42, "isFavorited": false, "favoriteCount": 342 }` (idempotent)

### 6.4 `GET /api/v1/members/me/recently-viewed` — 최근 본 장소

- **인증**: accessToken
- **Query**: `cursor`, `size=20`
- **Response 200**: `{ items: [...viewedAt], nextCursor, hasNext }`

### 6.5 `POST /api/v1/members/me/recently-viewed` — 기록 (upsert)

- **인증**: accessToken
- **Request**: `{ "placeId": 42 }`
- **Response 204**: 50 초과 시 가장 오래된 것 자동 삭제.

### 6.6 `DELETE /api/v1/members/me/recently-viewed/{placeId}` — 단건 삭제

- **Response 204**

### 6.7 `DELETE /api/v1/members/me/recently-viewed` — 전체 삭제

- **Response 204**

### 6.8 `POST /api/v1/members/me/recently-viewed/delete` — 다건 삭제

- **Request**: `{ "placeIds": [1,2,3] }`
- **Response 204**

---

## 7. Search / Saved Filter

### 7.1 `GET /api/v1/members/me/saved-filters` — 목록

- **인증**: accessToken
- **Query**: `sort=CREATED_AT_DESC | NAME_ASC | LAST_APPLIED_DESC` (기본 CREATED_AT_DESC), `size=20`
- **Response 200**:
  ```json
  {
    "items": [
      {
        "id": 42,
        "name": "강남 한식 소형견",
        "filter": { "categories": ["RESTAURANT"], "petFilter": { "species": ["DOG"], "sizes": ["SMALL"] } },
        "summaryText": "식당 · 강아지(소형)",
        "lastAppliedAt": "2026-04-17T11:00:00Z",
        "createdAt": "2026-03-10T08:00:00Z"
      }
    ],
    "totalCount": 7,
    "maxCount": 20,
    "hasNext": false
  }
  ```

### 7.2 `POST /api/v1/members/me/saved-filters` — 생성

- **인증**: accessToken
- **Request**:
  ```json
  {
    "name": "강남 한식 소형견",
    "filter": {
      "categories": ["RESTAURANT"],
      "petFilter": { "species": ["DOG"], "sizes": ["SMALL"] },
      "amenities": ["PARKING","TERRACE"],
      "minRating": 4.0
    }
  }
  ```
- **Response 201**: SavedFilterResponse (§7.1 item + `updatedAt`).
- **에러**: `SEARCH_001` (스키마, 400), `SEARCH_002` (이름 중복, 409), `SEARCH_003` (20개 초과, 409), `SEARCH_005` (빈 필터, 400)

### 7.3 `GET /api/v1/members/me/saved-filters/{id}` — 상세

- **인증**: accessToken
- **에러**: `SEARCH_004` (403), `SEARCH_006` (404)

### 7.4 `PATCH /api/v1/members/me/saved-filters/{id}` — 수정

- **인증**: accessToken
- **Request**: `{ "name": "...", "filter": { ... } }` (부분 업데이트)
- **Response 200**: SavedFilterResponse

### 7.5 `DELETE /api/v1/members/me/saved-filters/{id}` — 삭제

- **Response 204**

### 7.6 `POST /api/v1/members/me/saved-filters/delete` — 일괄 삭제

- **Request**: `{ "ids": [1,2,3] }` (최대 50개)
- **Response 204**

### 7.7 `filter_json` 스키마 (Phase 5 확정)

```jsonc
{
  "categories": ["RESTAURANT","CAFE"],
  "petFilter": {
    "species": ["DOG","CAT"],
    "sizes": ["SMALL","MEDIUM"],
    "weightKgMin": 1, "weightKgMax": 35
  },
  "petRequirements": ["NEUTERED","VACCINATED","REGISTERED_CHIP"],
  "amenities": ["PARKING","FENCED","TERRACE"],   // OR 매칭 (Phase 5 확정)
  "restrictions": {
    "indoorAllowed": true, "outdoorAllowed": true,
    "dangerousDogAllowed": false, "cageFree": true
  },
  "minRating": 4.0,
  "openNow": true,
  "location": { "lat": 37.5665, "lng": 126.9780, "radiusMeters": 3000 },
  "keyword": "강남",
  "sort": "distance"
}
```

---

## 8. Notification

### 8.1 `GET /api/v1/members/me/notifications` — 목록

- **인증**: accessToken
- **Query**: `cursor`, `size=20`, `onlyUnread=false`
- **Response 200**:
  ```json
  {
    "items": [
      {
        "id": 5001,
        "type": "REVIEW_LIKED",
        "title": "내 리뷰에 좋아요가 눌렸어요",
        "body": "강아지와 식당에 남긴 리뷰에 좋아요가 추가됐어요.",
        "payload": { "deepLink": "locapet://reviews/1001", "reviewId": 1001 },
        "isRead": false,
        "createdAt": "2026-04-17T11:00:00Z"
      }
    ],
    "nextCursor": "...", "hasNext": true
  }
  ```

### 8.2 `PATCH /api/v1/members/me/notifications/{id}/read` — 단건 읽음

- **인증**: accessToken
- **Response 204** (idempotent)

### 8.3 `PATCH /api/v1/members/me/notifications/read-all` — 전체 읽음

- **인증**: accessToken
- **Response 204**

### 8.4 `GET /api/v1/members/me/notifications/unread-count`

- **인증**: accessToken
- **Response 200**: `{ "unreadCount": 3 }`

### 8.5 `DELETE /api/v1/members/me/notifications/{id}`

- **인증**: accessToken
- **Response 204**

### 8.6 `DELETE /api/v1/members/me/notifications` — 전체 삭제

- **인증**: accessToken
- **Response 204**

### 8.7 `GET /api/v1/members/me/notification-settings` — 설정 조회

- **인증**: accessToken
- **Response 200**:
  ```json
  {
    "pushEnabled": true,
    "types": {
      "REVIEW_LIKED": true, "ANNOUNCEMENT": false,
      "INQUIRY_ANSWERED": true, "SYSTEM": true
    },
    "quietHoursStart": "22:00", "quietHoursEnd": "08:00"
  }
  ```

### 8.8 `PATCH /api/v1/members/me/notification-settings` — 부분 수정

- **Request**: 위 스키마 부분 업데이트.
- **Response 200**: 갱신된 설정.
- **에러**: `NOTIFICATION_003` (400), `NOTIFICATION_005` (시간 포맷, 400)

### 8.9 `POST /api/v1/members/me/device-tokens` — 디바이스 등록 (UPSERT)

- **인증**: accessToken
- **Request**:
  ```json
  {
    "platform": "IOS",
    "token": "fcm_or_apns_token_string",
    "appVersion": "1.2.3",
    "locale": "ko-KR"
  }
  ```
- **Response 200**: `{ "deviceTokenId": 999, "isActive": true }`
- **UPSERT 동작**: 동일 token 이 다른 member 에 있으면 `member_id` 이관.

### 8.10 `DELETE /api/v1/members/me/device-tokens/{tokenId}`

- **인증**: accessToken
- **Response 204** (`is_active=FALSE`)

---

## 9. Meta (공지 / FAQ / 약관 / 앱버전)

### 9.1 `GET /api/v1/meta/splash` — 스플래시 통합 (Phase 5 확정 신규)

- **인증**: none
- **Query**: `platform=IOS | ANDROID`
- **Response 200**:
  ```json
  {
    "appVersion": {
      "latest": "1.3.0", "minimum": "1.0.0",
      "forceUpdate": false, "storeUrl": "https://apps.apple.com/..."
    },
    "maintenance": null,
    "latestNotice": {
      "id": 15, "title": "v1.2.0 업데이트 안내", "category": "UPDATE",
      "priority": "PINNED", "publishedAt": "2026-04-15T00:00:00Z"
    },
    "pendingTermAgreements": []
  }
  ```
- **maintenance** 진행중일 때:
  ```json
  "maintenance": {
    "startAt": "2026-04-18T00:00:00Z",
    "endAt":   "2026-04-18T04:00:00Z",
    "message": "정기 점검 중입니다."
  }
  ```
- **캐시**: Redis `splash:meta::{platform}` TTL 5분.

### 9.2 `GET /api/v1/meta/app-versions/latest?platform=IOS`

- 기존 구현 유지. Splash 통합 대체 가능.

### 9.3 `GET /api/v1/meta/maintenances/current`

- 현재 진행 중인 점검 단건 또는 null.

### 9.4 `GET /api/v1/meta/notices` — 공지 목록

- **Query**: `cursor`, `size=20`, `category=GENERAL|UPDATE|EVENT|URGENT` (선택)
- **Response 200**: NoticeListItem 배열 + 페이지네이션.
- **정렬**: `priority='PINNED' DESC, published_at DESC, id DESC`.

### 9.5 `GET /api/v1/meta/notices/{id}` — 공지 상세

- **Response 200**: 위 item + `content` 전문.
- **에러**: `NOTICE_001` (404)

### 9.6 `GET /api/v1/meta/faqs` — FAQ 목록

- **Query**: `category=ACCOUNT|PET|PLACE|REVIEW|FAVORITE|NOTIFICATION|PAYMENT|ETC`, `q=검색어`
- **Response 200**: FAQ 배열 (Offset 기반).

### 9.7 `GET /api/v1/meta/faqs/categories` — 카테고리 탭 구성

- **Response 200**: `{ categories: [{ code, label, count }] }`

### 9.8 `GET /api/v1/meta/terms` — 현재 유효 약관 목록

- **Response 200**: Term 배열 (§9.9 형태).

### 9.9 `GET /api/v1/meta/terms/{code}` — 약관 본문

- **Path Param**: code ∈ `SERVICE_TERMS | PRIVACY_POLICY | LOCATION_TERMS | MARKETING_CONSENT | PUSH_CONSENT`
- **Response 200**:
  ```json
  {
    "id": 3,
    "code": "SERVICE_TERMS",
    "version": "1.2",
    "title": "이용약관",
    "content": "...(plain 또는 sanitized HTML)...",
    "isRequired": true,
    "effectiveFrom": "2026-04-01T00:00:00Z"
  }
  ```

### 9.10 `POST /api/v1/members/me/term-agreements` — 신규/재동의

- **인증**: accessToken
- **Request**:
  ```json
  { "agreements": [ { "termId": 6, "agreed": true } ] }
  ```
- **Response 200**: `{ "agreed": [6] }`

### 9.11 `GET /api/v1/members/me/term-agreements` — 내 동의 이력

- **인증**: accessToken
- **Response 200**: 동의 이력 배열.

### 9.12 `DELETE /api/v1/members/me/term-agreements/{termId}` — 선택 약관 철회

- **인증**: accessToken
- **Response 204**
- **에러**: `TERMS_003` (필수 약관, 403)

---

## 10. Inquiry

### 10.1 `GET /api/v1/inquiries` — 내 문의 목록

- **인증**: accessToken
- **Query**: `status=ALL | PENDING | ANSWERED | CLOSED`, `cursor`, `size=20`
- **Response 200**:
  ```json
  {
    "items": [
      {
        "id": 22, "category": "SERVICE_ERROR", "title": "앱이 자꾸 꺼져요",
        "status": "ANSWERED", "hasAnswer": true,
        "createdAt": "2026-04-15T09:00:00Z",
        "answeredAt": "2026-04-16T10:00:00Z"
      }
    ],
    "nextCursor": "...", "hasNext": true
  }
  ```

### 10.2 `POST /api/v1/inquiries` — 작성

- **인증**: accessToken
- **Request**:
  ```json
  {
    "category": "SUGGESTION",
    "title": "지도 기능 제안",
    "content": "...",
    "imageIds": ["img_01HX..."]
  }
  ```
- **category Enum (Phase 5 확정)**: `SERVICE_ERROR | PLACE_INFO_ERROR | ACCOUNT_ISSUE | REVIEW_REPORT_REQUEST | SUGGESTION | ETC`
  - **`FEATURE_REQUEST` → `SUGGESTION` 으로 변경**.
- **본문 제약**: 제목 1~50자, 본문 1~1000자, 이미지 최대 5장.
- **Response 201**: Inquiry 상세 (§10.4 형태).
- **Rate Limit**: 3분/30일 제안 — 초과 시 429 + `INQUIRY_008`.

### 10.3 `GET /api/v1/inquiries/{id}` — 상세

- **인증**: accessToken (본인만)
- **Response 200**: 본체 + 이미지 + 답변(있으면).

### 10.4 Inquiry 상세 응답

```json
{
  "id": 22,
  "category": "SERVICE_ERROR",
  "categoryLabel": "서비스 오류",
  "title": "앱이 자꾸 꺼져요",
  "content": "로그인 후 홈 화면에서 2~3초 뒤에 앱이 종료됩니다...",
  "images": [ { "id": 301, "imageUrl": "...", "displayOrder": 0 } ],
  "status": "ANSWERED",
  "answer": {
    "content": "안녕하세요, 로카펫입니다...",
    "createdAt": "2026-04-16T10:00:00Z",
    "updatedAt": "2026-04-16T10:00:00Z"
  },
  "createdAt": "2026-04-15T09:00:00Z",
  "updatedAt": "2026-04-15T09:00:00Z",
  "answeredAt": "2026-04-16T10:00:00Z",
  "closedAt": null
}
```

### 10.5 `PATCH /api/v1/inquiries/{id}` — 수정 (PENDING 만)

- **인증**: accessToken
- **Request**: 부분 업데이트.
- **Response 200**: Inquiry 상세.
- **에러**: `INQUIRY_003` (답변 후 수정, 409), `INQUIRY_004` (본인 아님, 403)

### 10.6 `DELETE /api/v1/inquiries/{id}` — 삭제 (PENDING 만)

- **Response 204**
- **에러**: `INQUIRY_003`, `INQUIRY_004`

### 10.7 `POST /api/v1/inquiries/{id}/close` — 닫기

- **사전조건**: `status=ANSWERED`
- **Response 200**: 갱신된 상세.
- **에러**: `INQUIRY_005` (PENDING 에서 close, 409)

---

## 11. 공통 — 업로드

### 11.1 `POST /api/v1/uploads/presigned-url` — Presigned URL 발급

- **인증**: accessToken (onboarding 단계는 onboardingAccessToken 도 허용)
- **Request**:
  ```json
  {
    "purpose": "REVIEW",
    "contentType": "image/jpeg",
    "fileSize": 2048576
  }
  ```
- **purpose Enum**: `PET_PROFILE | REVIEW | INQUIRY | MEMBER_PROFILE | NOTICE_THUMBNAIL`
- **Response 200**:
  ```json
  {
    "imageId": "img_01HX7K8S...",
    "uploadUrl": "https://locapet-media.s3.ap-northeast-2.amazonaws.com/...?X-Amz-Signature=...",
    "publicUrl": "https://cdn.locapet.app/reviews/01HX7K8S.jpg",
    "expiresIn": 300
  }
  ```
- **동작**: S3 Presigned PUT URL 발급 + 메타 레코드 (status=PENDING) 생성. `imageId` 는 후속 API (리뷰 작성 등) 에서 `imageIds` 배열에 포함. 24h 내 CCOMIT 안 되면 배치로 삭제.
- **제약**:
  - 허용 확장자: jpg, jpeg, png, webp, heic
  - 최대 파일 크기: 10MB
  - Presigned URL TTL: 5분
- **에러**: `UPLOAD_001` (파일 크기 초과, 400), `UPLOAD_003` (contentType 불일치, 400)

---

## 12. Admin API (admin-api)

**접근 제어**: ID/PW 로그인 + **IP 화이트리스트** + `MemberRole=ADMIN` + 감사 로그 필수.

### 12.1 `POST /admin/api/v1/auth/login`

- **Request**: `{ "username": "admin", "password": "..." }`
- **Response 200**: `{ "accessToken": "...", "expiresIn": 3600 }`

### 12.2 Place 관리

| Method | Path | 설명 |
|---|---|---|
| GET | `/admin/api/v1/places` | 전체(DRAFT/HIDDEN/CLOSED 포함) 목록, 필터 |
| POST | `/admin/api/v1/places` | DRAFT 생성 |
| GET | `/admin/api/v1/places/{id}` | 관리자 상세 (HIDDEN 포함 모두) |
| PATCH | `/admin/api/v1/places/{id}` | 필드 수정 |
| POST | `/admin/api/v1/places/{id}/publish` | DRAFT → PUBLISHED |
| POST | `/admin/api/v1/places/{id}/hide` | → HIDDEN |
| POST | `/admin/api/v1/places/{id}/close` | → CLOSED |
| DELETE | `/admin/api/v1/places/{id}` | 소프트 삭제 |
| PUT | `/admin/api/v1/places/{id}/hours` | 영업시간 일괄 치환 |
| PUT | `/admin/api/v1/places/{id}/amenities` | 편의시설 일괄 치환 |
| PUT | `/admin/api/v1/places/{id}/pet-policies` | 동반 정책 일괄 치환 |
| PUT | `/admin/api/v1/places/{id}/category-meta` | 카테고리 메타 교체 (JSONB) |
| POST | `/admin/api/v1/places/{id}/images` | 이미지 추가 |
| DELETE | `/admin/api/v1/places/{id}/images/{imageId}` | 이미지 제거 |
| POST | `/admin/api/v1/places/{id}/images/reorder` | 순서 변경 |

### 12.3 Place Curation

| Method | Path | 설명 |
|---|---|---|
| GET | `/admin/api/v1/place-curations` | 큐레이션 목록 |
| POST | `/admin/api/v1/place-curations` | 등록 |
| PATCH | `/admin/api/v1/place-curations/{id}` | 수정 |
| POST | `/admin/api/v1/place-curations/reorder` | 순서 일괄 변경 |
| DELETE | `/admin/api/v1/place-curations/{id}` | 삭제 |

### 12.4 공지/FAQ/약관 CMS

| Method | Path | 설명 |
|---|---|---|
| GET/POST/PATCH/DELETE | `/admin/api/v1/notices[/{id}]` | 공지 CRUD |
| POST | `/admin/api/v1/notices/{id}/publish` | 발행 + push 이벤트 |
| POST | `/admin/api/v1/notices/{id}/archive` | 아카이브 |
| GET/POST/PATCH/DELETE | `/admin/api/v1/faqs[/{id}]` | FAQ CRUD |
| PUT | `/admin/api/v1/faqs/reorder` | 순서 일괄 변경 |
| GET/POST/PATCH | `/admin/api/v1/terms[/{id}]` | 약관 CRUD |
| POST | `/admin/api/v1/terms/{id}/publish` | 약관 발행 (기존 버전 archive + 이벤트) |

### 12.5 Inquiry / Report

| Method | Path | 설명 |
|---|---|---|
| GET | `/admin/api/v1/inquiries` | 관리자 문의 목록 (필터/정렬) |
| GET | `/admin/api/v1/inquiries/{id}` | 관리자 상세 |
| POST | `/admin/api/v1/inquiries/{id}/answer` | 답변 등록 |
| PATCH | `/admin/api/v1/inquiries/{id}/answer` | 답변 수정 |
| DELETE | `/admin/api/v1/inquiries/{id}/answer` | 답변 삭제 (예외) |
| GET | `/admin/api/v1/reviews/reports` | 신고 리뷰 대시보드 |
| POST | `/admin/api/v1/reviews/{id}/hide` | 강제 HIDDEN |
| POST | `/admin/api/v1/reviews/{id}/restore` | ACTIVE 복구 |

### 12.6 Broadcast / 알림

| Method | Path | 설명 |
|---|---|---|
| POST | `/admin/api/v1/notifications/broadcast` | 전체 발송 (announcement/system) |
| POST | `/admin/api/v1/notifications/{id}/recall` | 회수 |

### 12.7 App Version / Maintenance

| Method | Path | 설명 |
|---|---|---|
| GET/POST/PATCH | `/admin/api/v1/app-versions[/{id}]` | 앱 버전 관리 |
| GET/POST/PATCH | `/admin/api/v1/maintenances[/{id}]` | 점검 관리 |

---

## 13. 에러 코드 총람

| Domain | 코드 | HTTP | 의미 |
|---|---|:---:|---|
| **AUTH** | AUTH_001 | 400 | 소셜 토큰 무효 |
| | AUTH_002 | 401 | refreshToken 만료/무효 |
| | AUTH_003 | 403 | CI 잠금(재가입 대기/영구차단) |
| | AUTH_004 | 401 | accessToken 무효/만료 |
| **VALIDATION** | VALIDATION_001 | 400 | 입력 검증 실패 (Bean Validation) |
| **MEMBER** | MEMBER_001 | 404 | 회원 없음 |
| | MEMBER_002 | 403 | 탈퇴 회원 |
| | MEMBER_003 | 409 | 닉네임 중복 |
| **IDENTITY** | IDENTITY_001 | 400 | PASS 검증 실패 |
| **ONBOARDING** | ONBOARDING_001 | 400 | 온보딩 세션 만료 |
| | ONBOARDING_002 | 400 | 온보딩 단계 불일치 |
| **PET** | PET_001 | 404 | 반려동물 없음 |
| | PET_002 | 400 | species 무효 |
| | PET_003 | 400 | species 변경 불가 |
| | PET_004 | 409 | 10마리 초과 |
| | PET_005 | 403 | 타인 Pet |
| | PET_006 | 400 | 대표 1마리 충돌 |
| | PET_007 | 400 | 이미 삭제됨 |
| **PLACE** | PLACE_001 | 404 | 업체 없음 |
| | PLACE_002 | 400 | 검색 조건 오류 |
| | PLACE_003 | 400 | 카테고리 무효 |
| | PLACE_004 | 409 | 폐업 업체 쓰기 |
| | PLACE_005 | 400 | distance 정렬에 좌표 필요 |
| | PLACE_006 | 403 | 관리자 전용 |
| | PLACE_007 | 409 | pet policy 중복 |
| | PLACE_008 | 400 | 좌표 범위 오류 |
| **REVIEW** | REVIEW_001 | 404 | 리뷰 없음 |
| | REVIEW_002 | 409 | 중복 리뷰 |
| | REVIEW_003 | 400 | 리뷰 입력 오류 |
| | REVIEW_004 | 403 | 본인 리뷰 아님 |
| | REVIEW_005 | 409 | 폐업 업체 리뷰 작성 |
| | REVIEW_006 | 400 | tailored 대표 Pet 필요 |
| | REVIEW_007 | 400 | petIds 유효성 |
| | REVIEW_008 | 400 | 본인 리뷰 좋아요 금지 |
| | REVIEW_009 | 400 | hasPet=true 인데 활성 Pet 0 |
| **REPORT** | REPORT_001 | 409 | 중복 신고 |
| **FAVORITE** | FAVORITE_001 | 404 | 통합 시 PLACE_001 로 대체 가능 |
| | FAVORITE_003 | 400 | 일괄 삭제 50 초과 |
| **SEARCH** | SEARCH_001 | 400 | filter_json 스키마 오류 |
| | SEARCH_002 | 409 | 이름 중복 |
| | SEARCH_003 | 409 | 20개 초과 |
| | SEARCH_004 | 403 | 타인 소유 |
| | SEARCH_005 | 400 | 빈 필터 |
| | SEARCH_006 | 404 | 필터 없음 |
| | SEARCH_007 | 400 | 키워드 50자 초과 |
| **NOTIFICATION** | NOTIFICATION_001 | 404 | 알림 없음 |
| | NOTIFICATION_002 | 403 | 타인 알림 |
| | NOTIFICATION_003 | 400 | 설정값 오류 |
| | NOTIFICATION_004 | 400 | 디바이스 토큰 형식 오류 |
| | NOTIFICATION_005 | 400 | 방해금지 시간 포맷 오류 |
| **NOTICE** | NOTICE_001 | 404 | 공지 없음 |
| | NOTICE_002 | 400 | 카테고리 오류 |
| **FAQ** | FAQ_001 | 404 | FAQ 없음 |
| | FAQ_002 | 400 | 카테고리 오류 |
| **TERMS** | TERMS_001 | 400 | 필수 약관 미동의 |
| | TERMS_002 | 404 | 약관 없음 |
| | TERMS_003 | 403 | 필수 약관 철회 불가 |
| | TERMS_004 | 409 | 이미 동의함 |
| | TERMS_005 | 400 | archive 버전 |
| | TERMS_006 | 409 | 재동의 필요 (쓰기 차단) |
| **INQUIRY** | INQUIRY_001 | 404 | 문의 없음 |
| | INQUIRY_002 | 400 | 입력 오류 |
| | INQUIRY_003 | 409 | 답변 후 수정/삭제 |
| | INQUIRY_004 | 403 | 본인 문의 아님 |
| | INQUIRY_005 | 409 | PENDING 에서 close |
| | INQUIRY_006 | 409 | 중복 답변 |
| | INQUIRY_007 | 400 | 이미지 5장 초과 |
| | INQUIRY_008 | 429 | Rate Limit |
| **UPLOAD** | UPLOAD_001 | 400 | 파일 크기 초과 |
| | UPLOAD_002 | 400 | 업로드 미완료 |
| | UPLOAD_003 | 400 | contentType 불일치 |
| **PLACE_REPORT** | PLACE_REPORT_001 | 409 | 중복 업체 신고 |

---

## 14. 공통 응답 샘플

### 14.1 성공 (단건)

```
HTTP/1.1 200 OK
Content-Type: application/json

{ "id": 42, "name": "..." }
```

### 14.2 성공 (페이지)

```json
{ "items": [...], "nextCursor": "...", "hasNext": true }
```

### 14.3 에러

```
HTTP/1.1 409 Conflict
Content-Type: application/json

{
  "errorCode": "REVIEW_002",
  "message": "이미 이 업체에 리뷰를 작성하셨어요."
}
```

### 14.4 필드 검증 에러 (details 포함)

```
HTTP/1.1 400 Bad Request

{
  "errorCode": "VALIDATION_001",
  "message": "nickname: must be between 2 and 20, content: length must be 10..2000",
  "details": {
    "fieldErrors": [
      { "field": "nickname", "reason": "SIZE", "message": "2~20자" },
      { "field": "content", "reason": "SIZE", "message": "10~2000자" }
    ]
  }
}
```

---

## 15. 엔드포인트 요약 (카운트)

| 영역 | 개수 |
|---|---|
| Auth / Onboarding | 6 |
| Member | 5 |
| Pet | 6 |
| Place (조회 + 신고) | 8 |
| Review | 10 |
| Wishlist / Recently Viewed | 8 |
| Saved Filter | 6 |
| Notification | 10 |
| Meta | 12 |
| Inquiry | 7 |
| Upload | 1 |
| **app-api 합계** | **79** |
| Admin API | 30+ |

---

## 16. 관련 문서

- [03. 공통 정책](./03-common-policies.md) — 에러 코드/페이지네이션/업로드 원칙
- [15. DB 스키마](./15-db-schema.md)
- [16. 구현 로드맵](./16-implementation-roadmap.md)
- [12. UX 플로우](./12-ux-flows.md)
- [13. 화면-API 매핑](./13-screen-api-map.md)
