# 06. 리뷰(Review) 기능 명세

> 본 문서는 로카펫의 **D9. 리뷰(Review)** 도메인 상세 기획이다.
> **핵심 가치 V2 — "내 반려동물에 맞춘 리뷰"** 를 구현하는 중심 도메인이다.
> 상위 문서: [01. 도메인 맵](./01-domain-map.md), [02. 용어집](./02-glossary.md), [03. 공통 정책](./03-common-policies.md)

---

## 1. 개요

### 1.1 도메인 책임

- 업체에 대한 **리뷰 작성 · 수정 · 삭제 · 조회** 를 제공한다.
- **맞춤리뷰(Tailored Review)** — 조회자의 대표 반려동물 속성(Species + Size)과 유사한 조건의 리뷰를 필터링하여 노출한다.
- **좋아요(도움됨)** 기능으로 리뷰 품질을 사용자 투표 기반으로 정렬(도움순).
- 리뷰 작성 시 반려동물 속성을 **스냅샷**으로 저장해 Pet 수정/삭제에 내성 확보.
- 신고 접수 및 상태 전이(`ACTIVE` / `HIDDEN` / `DELETED`) 관리.

### 1.2 다른 도메인과의 관계

| 방향 | 대상 도메인 | 관계 |
|---|---|---|
| 참조 | Member(D3) | `reviews.member_id` → FK (작성자) |
| 참조 | Place(D5) | `reviews.place_id` → FK |
| 참조 | Pet(D4) | `review_pets.pet_id` → FK + 스냅샷 복제 |
| 피참조 | Report(D12) | `reports.target_id` (targetType=REVIEW) |
| 피드백 | Place(D5) | `rating_avg`, `review_count` 캐시 갱신 |

---

## 2. 엔티티 & 데이터 모델

### 2.1 `reviews`

| 컬럼 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `id` | BIGSERIAL | O | - | PK |
| `place_id` | BIGINT | O | - | FK → places.id |
| `member_id` | BIGINT | O | - | FK → members.id (작성자) |
| `rating` | SMALLINT | O | - | 1 ~ 5 정수 |
| `content` | TEXT | O | - | 본문. 10 ~ 2000자 |
| `visit_date` | DATE | X | NULL | 방문 일자 (선택 입력). 타임존 무관 `LocalDate` |
| `like_count` | INT | O | 0 | 좋아요 수 캐시 |
| `report_count` | INT | O | 0 | 신고 수 캐시 (자동 HIDDEN 임계값 용) |
| `status` | VARCHAR(20) | O | `ACTIVE` | `ACTIVE` / `HIDDEN` / `DELETED` |
| `hidden_reason` | VARCHAR(50) | X | NULL | `REPORTED_THRESHOLD` / `ADMIN_ACTION` 등 |
| `created_at` | TIMESTAMPTZ | O | NOW() | |
| `updated_at` | TIMESTAMPTZ | O | NOW() | |
| `deleted_at` | TIMESTAMPTZ | X | NULL | 소프트 삭제 |

### 2.2 `review_images`

| 컬럼 | 타입 | NOT NULL | 설명 |
|---|---|:---:|---|
| `id` | BIGSERIAL | O | PK |
| `review_id` | BIGINT | O | FK |
| `image_url` | VARCHAR(500) | O | S3/CDN URL |
| `display_order` | INT | O | 0 ~ 9 |
| `created_at` | TIMESTAMPTZ | O | |

- UNIQUE (`review_id`, `display_order`).

### 2.3 `review_pets` (리뷰 ↔ 반려동물 N:M + 스냅샷)

| 컬럼 | 타입 | NOT NULL | 설명 |
|---|---|:---:|---|
| `review_id` | BIGINT | O | FK |
| `pet_id` | BIGINT | O | FK (소프트 삭제된 Pet 도 유지) |
| `pet_name_snapshot` | VARCHAR(20) | O | 작성 시점 이름 |
| `species_snapshot` | VARCHAR(10) | O | `DOG`/`CAT` |
| `size_snapshot` | VARCHAR(10) | O | `SMALL`/`MEDIUM`/`LARGE` |
| `breed_snapshot` | VARCHAR(50) | X | 품종 스냅샷 (노출용) |
| `created_at` | TIMESTAMPTZ | O | |

- PK: (`review_id`, `pet_id`).
- **스냅샷이 "진실의 원본(source of truth)"** — 조회 시 현재 Pet 값이 아니라 스냅샷을 사용.

### 2.4 `review_likes` (좋아요 / 도움됨)

| 컬럼 | 타입 | NOT NULL | 설명 |
|---|---|:---:|---|
| `review_id` | BIGINT | O | FK |
| `member_id` | BIGINT | O | FK |
| `created_at` | TIMESTAMPTZ | O | |

- PK: (`review_id`, `member_id`).
- 리뷰 삭제 시에도 좋아요 데이터 보존(통계 목적). 단, 응답에는 미노출.

### 2.5 `review_reports`

| 컬럼 | 타입 | NOT NULL | 설명 |
|---|---|:---:|---|
| `id` | BIGSERIAL | O | PK |
| `review_id` | BIGINT | O | FK |
| `reporter_member_id` | BIGINT | O | FK |
| `reason_code` | VARCHAR(30) | O | `SPAM` / `ABUSE` / `FALSE_INFO` / `PRIVACY` / `ETC` |
| `memo` | VARCHAR(500) | X | 자유 메모 |
| `status` | VARCHAR(20) | O | `PENDING` / `REVIEWED` / `REJECTED` |
| `created_at` | TIMESTAMPTZ | O | |
| `reviewed_at` | TIMESTAMPTZ | X | |

- UNIQUE (`review_id`, `reporter_member_id`, `reason_code`) — 동일 사유 중복 신고 차단.

### 2.6 Enum 정의

```kotlin
enum class ReviewStatus { ACTIVE, HIDDEN, DELETED }
enum class ReviewSort { LATEST, RATING_HIGH, HELPFUL, TAILORED }
enum class ReviewReportReason { SPAM, ABUSE, FALSE_INFO, PRIVACY, ETC }
```

### 2.7 제약 조건 & 인덱스

| 종류 | 표현 | 목적 |
|---|---|---|
| CHECK | `rating BETWEEN 1 AND 5` | |
| CHECK | `char_length(content) BETWEEN 10 AND 2000` | |
| CHECK | `like_count >= 0`, `report_count >= 0` | |
| UNIQUE (부분) | `CREATE UNIQUE INDEX uk_reviews_place_member_active ON reviews (place_id, member_id) WHERE deleted_at IS NULL` | **1 회원 × 1 업체 = 1 리뷰** 물리 보장 |
| INDEX | `idx_reviews_place_status_created (place_id, status, created_at DESC, id DESC)` | 업체별 최신순 리뷰 목록 |
| INDEX | `idx_reviews_place_status_rating (place_id, status, rating DESC, id DESC)` | 평점순 |
| INDEX | `idx_reviews_place_status_like (place_id, status, like_count DESC, id DESC)` | 도움순 |
| INDEX | `idx_review_pets_species_size (species_snapshot, size_snapshot)` | 맞춤리뷰 매칭 |
| INDEX | `idx_reviews_member (member_id, created_at DESC)` | 내 리뷰 목록 |

### 2.8 상태 머신

```
     write                report ≥ N
  ┌──────────┐           ┌────────────┐
  │  (none)  │──────────▶│   ACTIVE   │────────┐
  └──────────┘           └─────┬──────┘        │
                               │ admin hide    │ auto hide
                               ▼               ▼
                         ┌───────────────────────┐
                         │        HIDDEN         │
                         └──────────┬────────────┘
                                    │ admin restore
                                    ▼
                               ┌────────┐
                               │ ACTIVE │
                               └────────┘

   user/admin delete (any state)
          │
          ▼
     ┌─────────┐
     │ DELETED │  (soft: deleted_at = NOW())
     └─────────┘
```

- `DELETED` 는 조회에서 제외. `uk_reviews_place_member_active` 부분 UNIQUE 가 `deleted_at IS NULL` 조건이므로 **삭제 후에도 재작성 불가 규칙을 유지하려면 `deleted_at IS NULL` 조건을 빼야 함**.
- **결정**: 소프트 삭제된 리뷰도 재작성 차단을 유지하기 위해 **UNIQUE 인덱스 조건에서 `deleted_at IS NULL` 제외** — 즉 `(place_id, member_id)` 전역 UNIQUE 로 가는 안.
- 운영 상 리뷰 삭제 시 본문/이미지는 비우되 row 는 유지 + `status=DELETED` 로 표시. 이 쪽이 정책 "재작성 불가" 와 일관. ⚠️ **§4.1 에서 이 정책을 불변식으로 명시**.

---

## 3. 주요 기능 (Use Case)

### 3.1 리뷰 작성

- **대상 화면**:
  [작성 진입 A](node-id=479:42852), [작성 B](node-id=603:45278),
  [작성 C](node-id=603:45369), [작성 D](node-id=603:45483),
  [작성 완료](node-id=479:42712), [작성 취소/삭제 모달](node-id=479:42776)
- **사전조건**:
  - 로그인 + `onboardingStage = COMPLETED`.
  - 대상 Place 가 `PUBLISHED` (CLOSED 는 `REVIEW_005`).
  - 해당 Place 에 본인 기존 리뷰 없음(DELETED 포함 전역 검사).
  - **최소 1마리 이상의 활성 Pet 등록** 필요 (맞춤리뷰 근간 보장) ❓ 아니면 Pet 0마리도 허용할지 정책 확인.

- **입력**:
  ```json
  {
    "placeId": 42,
    "rating": 5,
    "content": "...",
    "visitDate": "2026-04-15",
    "petIds": [12, 13],
    "imageIds": ["img_01HX...", "img_01HY..."]
  }
  ```
- **동작**:
  1. 중복 리뷰 검사 — `reviews WHERE place_id = ? AND member_id = ?` 존재 시 `REVIEW_002` (409).
  2. `petIds` 모두 본인 소유 & 활성(`deleted_at IS NULL`) 인지 검증.
  3. 각 Pet 의 현재 속성을 `review_pets.*_snapshot` 으로 복제.
  4. `review_images` 에 이미지 연결 (최대 10장).
  5. `places.review_count += 1`, `rating_avg` 재계산 (동일 트랜잭션).
- **출력**: `ReviewResponse` (아래 §5.3).
- **권한**: accessToken 필수.

### 3.2 리뷰 수정

- **대상 화면**: [리뷰 수정](node-id=479:43075), [수정 상세](node-id=479:43139)
- **수정 가능 필드**: `rating`, `content`, `visit_date`, `petIds`(연결 반려동물), 이미지 목록.
- **수정 불가**: `place_id`, `member_id`.
- **스냅샷 처리**: `petIds` 가 변경되면 `review_pets` 를 **교체** — 추가된 Pet 은 현재 속성 스냅샷 신규 생성, 제거된 Pet row 삭제, 유지되는 Pet 은 **기존 스냅샷을 그대로 유지**(수정 시점에 갱신하지 않음).
  - ❓ 정책 재검토 여지 있음: "수정 시점에 전체 재스냅샷" 으로 바꾸면 최신 정보 반영 가능. 현재는 "원 경험 보존" 쪽으로 제안.
- **재작성 허용 여부**: 재작성 금지 원칙 — 삭제 후 새로 쓰는 플로우 불가.
- **권한**: 본인 리뷰만 (`REVIEW_004` 403).

### 3.3 리뷰 삭제

- **소프트 삭제** — `status = DELETED`, `deleted_at = NOW()`, `content = ''`, `review_images` 전체 soft delete.
- **집계 캐시 갱신**: `places.review_count -= 1`, `rating_avg` 재계산.
- **좋아요는 보존** (통계 목적). 다만 DELETED 상태 리뷰는 목록/상세에서 제외.
- **재작성 불가 원칙 유지**: 같은 `(place_id, member_id)` 로 신규 INSERT 시도 시 `REVIEW_002` (409).
- **권한**: 본인 또는 관리자.

### 3.4 업체별 리뷰 목록

- **대상 화면**:
  [전체 리뷰](node-id=479:23645), [전체 리뷰 스크롤](node-id=479:23816),
  [리뷰 스크롤 상세](node-id=479:26205),
  [맞춤리뷰만](node-id=565:42655)

- **입력**:
  ```
  GET /api/v1/places/{placeId}/reviews
    ?sort=latest|rating_high|helpful|tailored
    &cursor=...&size=20
  ```
- **동작**:
  - `status = 'ACTIVE'` 필터 고정.
  - `sort` 별 인덱스 활용 (§2.7).
  - `sort=tailored`: 로그인 필수. 로그인 유저의 **대표 Pet** 을 조회 → `review_pets.species_snapshot = primary.species AND review_pets.size_snapshot = primary.size` 조건 조인.
    - 대표 Pet 없으면 `REVIEW_006` (400) — "대표 반려동물을 먼저 설정해주세요."
  - 응답에 각 리뷰의 `likedByMe: boolean` 포함 (로그인 시).

- **응답 아이템 축약**:
  ```json
  {
    "id": 1001,
    "rating": 5,
    "content": "...",
    "visitDate": "2026-04-15",
    "images": [{ "imageUrl": "...", "displayOrder": 0 }],
    "author": {
      "memberId": 7,
      "nickname": "초코맘",
      "profileImageUrl": "..."
    },
    "pets": [
      { "petId": 12, "name": "초코", "species": "DOG", "size": "SMALL", "breed": "포메라니안" }
    ],
    "likeCount": 34,
    "likedByMe": true,
    "createdAt": "2026-04-16T08:20:00Z"
  }
  ```

### 3.5 리뷰 좋아요 / 취소

- **엔드포인트**: `POST /api/v1/reviews/{id}/like`, `DELETE /api/v1/reviews/{id}/like`
- **동작**:
  - INSERT/DELETE on `review_likes` + `reviews.like_count ±= 1` (동일 트랜잭션).
  - 동일 회원이 이미 좋아요 → 200 idempotent (또는 409 — 정책 선택 ❓. MVP: idempotent 200 제안).
  - 본인 리뷰에도 좋아요 가능 ❓ — UX 차원에서 금지하는 편이 자연스러움. `REVIEW_008` 제안.
- **권한**: accessToken 필수.

### 3.6 내 리뷰 목록

- **대상 화면**:
  [나의 리뷰](node-id=479:43215), [나의 리뷰 상세](node-id=479:43311),
  [수정 진입](node-id=479:43424), [삭제 확인](node-id=479:43521)
- **입력**: `GET /api/v1/members/me/reviews?cursor=...&size=20`
- **응답**: 본인 작성 리뷰 전체 (ACTIVE + HIDDEN 포함. DELETED 제외).
- **권한**: accessToken 필수.

### 3.7 리뷰 신고

- **엔드포인트**: `POST /api/v1/reviews/{id}/report`
- **입력**: `{ "reasonCode": "ABUSE", "memo": "..." }`
- **동작**:
  - 동일 (리뷰, 신고자, 사유) 중복 방지 — UNIQUE 위반 시 `REPORT_001` (409).
  - `reviews.report_count += 1`. 임계값 도달 시 자동 `status = HIDDEN` + `hidden_reason = 'REPORTED_THRESHOLD'`.
  - 임계값 ❓: **MVP 제안 3건**.
- **권한**: accessToken 필수.
- 상세 정책은 [03-common-policies.md §3.2](./03-common-policies.md) 참조. Support(D12) 문서에서 신고 통합 처리.

### 3.8 맞춤리뷰 — 핵심 로직 상세

```sql
-- 예시 (cursor 기반, ACTIVE, 대표 Pet 매칭)
SELECT r.*
FROM reviews r
JOIN review_pets rp ON rp.review_id = r.id
WHERE r.place_id = :placeId
  AND r.status = 'ACTIVE'
  AND rp.species_snapshot = :primarySpecies
  AND rp.size_snapshot = :primarySize
  AND (r.created_at, r.id) < (:cursorCreatedAt, :cursorId)
ORDER BY r.created_at DESC, r.id DESC
LIMIT :size + 1;
```

- **DISTINCT 필요성**: 리뷰 1건이 여러 Pet 을 연결해도 `species+size` 는 중복 매칭될 수 있으므로 `SELECT DISTINCT r.*` 또는 `EXISTS` 서브쿼리 사용 권장.
- **"유사 크기"**: 현재는 "동일 크기(=)" 로 정의. 향후 SMALL↔MEDIUM 도 느슨하게 매칭하는 정책 도입 검토.
- **Fallback**: 매칭 리뷰 0건 시 응답 `items: []` + UX 상 "조건에 맞는 리뷰가 없어요" Empty (클라이언트 책임).

---

## 4. 비즈니스 규칙 & 제약

### 4.1 도메인 불변식

1. **1 회원 × 1 업체 = 1 리뷰**. 소프트 삭제 후에도 재작성 불가. (`(place_id, member_id)` 전역 UNIQUE)
2. **평점은 1~5 정수**. 소수점 허용 안 함.
3. **본문 10 ~ 2000자**.
4. **이미지 최대 10장**.
5. **연결 반려동물 최소 1마리, 최대 5마리** ❓ (회원당 최대 활성 Pet 이 10마리이므로 5마리 상한 제안).
6. **Pet 스냅샷은 작성 시점 기준** — 이후 Pet 수정은 스냅샷에 반영되지 않음 (수정 시 petIds 추가·제거 예외).
7. **CLOSED 업체에는 신규 리뷰 작성 불가** (기존 리뷰 조회·수정·삭제는 허용).
8. **맞춤리뷰(sort=tailored) 는 로그인 + 대표 Pet 필수**.
9. **삭제된 리뷰의 좋아요는 보존**, 단 노출에서 제외.
10. **자동 HIDDEN 임계값: 신고 3건** (조정 가능).

### 4.2 입력 제약

| 필드 | 제약 |
|---|---|
| `rating` | 1~5 정수 |
| `content` | 10~2000자, trim 후 길이 검증 |
| `petIds` | 1~5개, 본인 소유 + 활성 |
| `imageIds` | 0~10개, COMMIT 완료된 업로드 |
| `visit_date` | `<= TODAY` (미래 방지) |

### 4.3 집계 캐시 갱신

- 리뷰 CRUD 시 `places.rating_avg` 는 다음 중 하나로 재계산:
  - (a) `UPDATE places SET rating_avg = (SELECT AVG(rating)::NUMERIC(2,1) FROM reviews WHERE place_id = ? AND status = 'ACTIVE')` — 단순, 정확.
  - (b) 증분 계산 (count 와 sum 별도 보관) — 고빈도 업체 유리.
- **MVP**: (a) 방식 채택. 추후 필요 시 (b) 로 전환.

---

## 5. API 엔드포인트 초안 (REST)

| Method | Path | Auth | 설명 |
|---|---|:---:|---|
| POST | `/api/v1/reviews` | accessToken | 리뷰 작성 |
| GET | `/api/v1/places/{placeId}/reviews` | optional | 업체별 리뷰 목록 (sort 포함) |
| GET | `/api/v1/reviews/{id}` | optional | 리뷰 상세 |
| PATCH | `/api/v1/reviews/{id}` | accessToken | 수정 (본인) |
| DELETE | `/api/v1/reviews/{id}` | accessToken | 삭제 (본인 또는 관리자) |
| POST | `/api/v1/reviews/{id}/like` | accessToken | 좋아요 |
| DELETE | `/api/v1/reviews/{id}/like` | accessToken | 좋아요 취소 |
| POST | `/api/v1/reviews/{id}/report` | accessToken | 신고 |
| GET | `/api/v1/members/me/reviews` | accessToken | 내 리뷰 목록 |

### 5.1 요청 바디 예시 (POST `/api/v1/reviews`)

```json
{
  "placeId": 42,
  "rating": 5,
  "content": "강아지와 함께 가기 정말 좋은 곳이었어요...",
  "visitDate": "2026-04-15",
  "petIds": [12, 13],
  "imageIds": ["img_01HX...", "img_01HY..."]
}
```

### 5.2 요청 바디 예시 (PATCH `/api/v1/reviews/{id}`)

```json
{
  "rating": 4,
  "content": "수정된 본문...",
  "visitDate": "2026-04-15",
  "petIds": [12],
  "imageIds": ["img_01HX..."]
}
```

### 5.3 응답 스키마 (상세)

```json
{
  "id": 1001,
  "placeId": 42,
  "rating": 5,
  "content": "...",
  "visitDate": "2026-04-15",
  "images": [
    { "id": 9001, "imageUrl": "...", "displayOrder": 0 }
  ],
  "pets": [
    {
      "petId": 12,
      "name": "초코",
      "species": "DOG",
      "size": "SMALL",
      "breed": "포메라니안"
    }
  ],
  "author": {
    "memberId": 7,
    "nickname": "초코맘",
    "profileImageUrl": "..."
  },
  "likeCount": 34,
  "likedByMe": true,
  "status": "ACTIVE",
  "createdAt": "2026-04-16T08:20:00Z",
  "updatedAt": "2026-04-16T08:20:00Z"
}
```

---

## 6. 에러 코드

| 코드 | HTTP | 메시지 | 발생 상황 |
|---|:---:|---|---|
| `REVIEW_001` | 404 | 리뷰를 찾을 수 없어요. | 없는 id, DELETED |
| `REVIEW_002` | 409 | 이미 이 업체에 리뷰를 작성하셨어요. | `(place_id, member_id)` 중복 |
| `REVIEW_003` | 400 | 리뷰 조건을 확인해주세요. | 평점 범위/본문 길이/이미지 수 등 |
| `REVIEW_004` | 403 | 본인의 리뷰만 수정·삭제할 수 있어요. | 타인 리뷰 변경 |
| `REVIEW_005` | 409 | 폐업한 업체에는 리뷰를 작성할 수 없어요. | `PlaceStatus = CLOSED` |
| `REVIEW_006` | 400 | 대표 반려동물을 먼저 설정해주세요. | `sort=tailored` 인데 대표 Pet 없음 |
| `REVIEW_007` | 400 | 선택한 반려동물을 확인해주세요. | petIds 중 타인 소유 또는 삭제된 Pet |
| `REVIEW_008` | 400 | 본인의 리뷰에는 좋아요를 할 수 없어요. | 자기 리뷰 좋아요 (❓ 정책 결정에 따름) |
| `REVIEW_009` | 400 | 리뷰 작성에는 반려동물 등록이 필요해요. | 활성 Pet 0마리 |
| `REPORT_001` | 409 | 이미 신고한 리뷰예요. | 중복 신고 |
| `UPLOAD_002` | 400 | 업로드가 완료되지 않은 이미지예요. | COMMIT 안 된 imageId |

---

## 7. 엣지 케이스 & 예외 처리

| 케이스 | 처리 |
|---|---|
| **작성 시점 Pet 의 species 가 CAT 이었는데 이후 DOG 로 바뀐 경우** | 불가능 — Species 는 변경 불가(Pet §4.1). |
| **작성 시점 Pet 이 삭제됨** | 스냅샷이 유지되므로 리뷰 조회에 문제 없음. 응답 `pets[].petId` 는 그대로 노출되되, 탭해서 Pet 상세 진입 시도 시 404. |
| **대표 Pet 이 없을 때 리뷰 목록 조회 `sort=tailored`** | 400 `REVIEW_006`. 클라이언트는 "대표 반려동물을 먼저 설정해주세요" 안내. |
| **신고 임계값 도달 → 자동 HIDDEN** | 트랜잭션 내 COUNTER 증가 직후 `status = HIDDEN`. 다음 요청부터 목록에서 제외. |
| **관리자 강제 HIDDEN 후 작성자 수정 시도** | 수정 자체는 허용 (작성자 본인) — 수정 후에도 HIDDEN 유지. 복구는 관리자만. |
| **동일 회원이 좋아요 중복 누름** | INSERT UNIQUE 위반 시 200 idempotent 처리. |
| **좋아요 삭제 동시성** | 낙관적으로 DELETE + affected rows == 0 이면 idempotent 200. |
| **리뷰 수정 시 이미지 전체 교체** | `review_images` 전량 soft delete 후 신규 INSERT. 기존 URL 은 배치 정리. |
| **리뷰 본문 금칙어/개인정보 패턴** | [03-common-policies.md §3.1](./03-common-policies.md) 정책 미확정 — 서버 측 간단 패턴 매칭(전화번호) 만 MVP 제안. |
| **Place 가 HIDDEN 상태인데 그 업체 리뷰 조회** | Place 조회 자체가 404 이므로 리뷰 목록도 404 (`PLACE_001`). 작성자 본인의 "내 리뷰 목록" 에는 노출. |
| **평점만 바뀌고 집계 캐시 업데이트 실패** | 트랜잭션 롤백. 재시도 또는 야간 배치 재계산. |

---

## 8. 성능 / 인덱싱 고려사항

### 8.1 주요 쿼리 패턴

| 패턴 | 빈도 | 인덱스 |
|---|:---:|---|
| 업체별 최신순 | **매우 높음** | `idx_reviews_place_status_created` (커버링) |
| 업체별 평점순 | 중간 | `idx_reviews_place_status_rating` |
| 업체별 도움순 | 중간 | `idx_reviews_place_status_like` |
| 맞춤리뷰 (JOIN review_pets) | 중간~높음 | `idx_review_pets_species_size` + 업체 인덱스 |
| 내 리뷰 목록 | 중간 | `idx_reviews_member` |
| 좋아요 추가/삭제 | 높음 | PK `(review_id, member_id)` |

### 8.2 캐시

- 리뷰 목록은 **캐시 대상 아님**(실시간성 중요 + 페이지네이션).
- `reviews.like_count`, `reviews.report_count` 는 DB 컬럼 캐시.
- 대표 Pet 캐시는 [04-pet-spec.md §8.2](./04-pet-spec.md) — 맞춤리뷰 필터링에 재사용.

### 8.3 동시성

- `like_count` 증감: `UPDATE ... SET like_count = like_count ± 1`. PK 락만 발생.
- `review_count` / `rating_avg` 갱신은 Place 레벨 락 유발 가능 — 트랜잭션 최소화, 재시도 로직.
- 고빈도 업체의 평점 계산은 `AVG` 집계로 인한 스캔 비용 — `idx_reviews_place_status_created` 가 커버.

### 8.4 스케일 대비

- 리뷰 1천만건 이상 시 파티셔닝(`place_id % N`) 고려 (Phase 5+).
- 맞춤리뷰 JOIN 성능 저하 시 `reviews` 에 `species_primary`, `size_primary` 역정규화 컬럼 추가 고려.

---

## 9. 의존성 & 향후 확장

### 9.1 의존 도메인

- **Member, Pet, Place** — FK 참조.
- **Upload** — Presigned 이미지 업로드.
- **Notification (Phase 2+)** — 내 리뷰에 좋아요/답글 시 푸시.
- **Support/Report** — 신고 통합 처리.

### 9.2 확장 포인트

| 확장 | Phase | 비고 |
|---|:---:|---|
| 리뷰 답글 (업체 오너 / 관리자) | Phase 3+ | `review_replies` 테이블 |
| 리뷰 북마크 / 공유 | Phase 4+ | |
| 도움순 시간감쇠 (Hacker News 스타일) | Phase 4+ | 최신 + 좋아요 균형 |
| 맞춤리뷰 매칭 완화 (유사 크기 범위) | Phase 3+ | SMALL↔MEDIUM 도 허용 |
| 기질 태그 매칭 추가 | Phase 5+ | 현재는 Species+Size |
| AI 기반 자동 스팸 필터 | Phase 5+ | |
| 영상 리뷰 | Phase 5+ | |

### 9.3 ❓ 확인 필요 항목

1. **리뷰 작성 시 활성 Pet 0마리 허용 여부** — 현재 제안: 필수.
2. **연결 반려동물 최대 개수** — 5마리 제안.
3. **본인 리뷰에 좋아요 허용** — 금지 제안.
4. **신고 자동 HIDDEN 임계값** — 3건 제안. 운영 후 조정.
5. **수정 시 Pet 스냅샷 재생성 여부** — 현재 "기존 유지" 제안.
6. **좋아요 중복 POST 응답** — idempotent 200 제안.
7. **맞춤리뷰 "유사 크기" 정의** — 현재 "동일 크기". 향후 완화 검토.
8. **`uk_reviews_place_member_active` 의 WHERE 조건 최종 결정** — 전역 UNIQUE (재작성 불가) vs 활성만 UNIQUE (삭제 후 재작성 허용). 본 문서는 **전역 UNIQUE** 로 최종 제안.
