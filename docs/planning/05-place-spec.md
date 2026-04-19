# 05. 업체(Place) 기능 명세

> 본 문서는 로카펫의 **D5. 업체/상세(Place)** 도메인 상세 기획이다. Place 는 리뷰·찜·검색·지도·피드 등 거의 모든 기능의 중심축이다.
> 상위 문서: [01. 도메인 맵](./01-domain-map.md), [02. 용어집](./02-glossary.md), [03. 공통 정책](./03-common-policies.md)

---

## 1. 개요

### 1.1 도메인 책임

- **반려동물 동반 가능 장소**의 마스터 데이터를 관리한다.
- 업체의 기본 정보(이름·주소·연락처), 위치 좌표, 카테고리, 동반 반려동물 정책, 편의시설, 운영시간, 사진 등을 보유한다.
- 데이터 작성/수정 권한은 **관리자(admin-api)** 에 국한되며, 일반 회원은 **읽기 + 찜/리뷰/신고** 만 가능하다.
- "로카 추천 장소", "요즘 찜 많은 장소" 등 **큐레이션 피처의 원천 데이터**를 제공한다.

### 1.2 다른 도메인과의 관계

| 방향 | 대상 도메인 | 관계 |
|---|---|---|
| 피참조 | Review(D9) | `reviews.place_id` → FK |
| 피참조 | Favorite(D10) | `wishlists.place_id` → FK |
| 피참조 | RecentlyViewed(D10) | `recently_viewed_places.place_id` → FK |
| 피참조 | Feed(D6) / Search(D7) / Map(D8) | 업체 정보를 조회 · 정렬 · 공간 검색 |
| 피참조 | Report(D12) | `reports.target_id` (targetType=PLACE) |

---

## 2. 엔티티 & 데이터 모델

### 2.1 `places` (업체 본체)

| 컬럼 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `id` | BIGSERIAL | O | - | PK |
| `name` | VARCHAR(100) | O | - | 업체명 |
| `category` | VARCHAR(20) | O | - | Enum: `RESTAURANT` / `CAFE` / `KINDERGARTEN` / `LODGING` / `PARK` |
| `status` | VARCHAR(20) | O | `DRAFT` | `DRAFT` / `PUBLISHED` / `HIDDEN` / `CLOSED` |
| `address` | VARCHAR(255) | O | - | 지번 주소 |
| `road_address` | VARCHAR(255) | X | NULL | 도로명 주소 |
| `detail_address` | VARCHAR(100) | X | NULL | 상세 주소 (동/호수) |
| `region_code` | VARCHAR(10) | X | NULL | 시/군/구 코드 (검색 필터용) |
| `location` | `geography(Point,4326)` | O | - | PostGIS 포인트. WGS84 기반 (네이버 지도 호환) |
| `phone` | VARCHAR(20) | X | NULL | 전화번호 (E.164 또는 국내 포맷 normalize) |
| `website` | VARCHAR(500) | X | NULL | 홈페이지/예약 링크 |
| `kakao_channel_id` | VARCHAR(100) | X | NULL | 카카오톡 채널 ID (딥링크용) |
| `email` | VARCHAR(255) | X | NULL | |
| `description` | TEXT | X | NULL | 업체 소개 본문 |
| `thumbnail_image_url` | VARCHAR(500) | X | NULL | 대표 이미지 |
| `owner_member_id` | BIGINT | X | NULL | 업체 오너 연결 (향후 파트너 기능. MVP 미사용) |
| `rating_avg` | NUMERIC(2,1) | O | 0.0 | 평점 캐시 (리뷰 작성/수정/삭제 시 갱신) |
| `review_count` | INT | O | 0 | 활성 리뷰 수 캐시 |
| `favorite_count` | INT | O | 0 | 찜 수 캐시 (🅡 Wishlist 에서 갱신) |
| `created_at` | TIMESTAMPTZ | O | NOW() | |
| `updated_at` | TIMESTAMPTZ | O | NOW() | |
| `deleted_at` | TIMESTAMPTZ | X | NULL | 소프트 삭제 |

### 2.2 `place_images`

| 컬럼 | 타입 | NOT NULL | 설명 |
|---|---|:---:|---|
| `id` | BIGSERIAL | O | PK |
| `place_id` | BIGINT | O | FK → places.id |
| `image_url` | VARCHAR(500) | O | |
| `display_order` | INT | O | 0부터 오름차순 |
| `created_at` | TIMESTAMPTZ | O | |

- UNIQUE (`place_id`, `display_order`).

### 2.3 `place_hours` (운영 시간)

| 컬럼 | 타입 | NOT NULL | 설명 |
|---|---|:---:|---|
| `place_id` | BIGINT | O | FK |
| `day_of_week` | SMALLINT | O | 0=Sun ~ 6=Sat (ISO DAYOFWEEK). ❓일/월 시작 컨벤션 확정 |
| `open_time` | TIME | X | 당일 영업 시작. `is_closed = TRUE` 면 NULL |
| `close_time` | TIME | X | 종료 (익일 새벽은 > 24h 표현 또는 is_next_day 플래그 ❓) |
| `is_closed` | BOOLEAN | O | 정기 휴무일 |
| `note` | VARCHAR(100) | X | "브레이크타임 15~17시" 등 자유 메모 |

- PK: (`place_id`, `day_of_week`).

### 2.4 `place_amenities` (편의시설 태그)

| 컬럼 | 타입 | NOT NULL | 설명 |
|---|---|:---:|---|
| `place_id` | BIGINT | O | FK |
| `amenity_code` | VARCHAR(30) | O | Enum: `PARKING` / `TERRACE` / `INDOOR` / `OUTDOOR` / `FENCED` / `TREATS` / `WATER_BOWL` / `POOP_BAG` / `PLAYGROUND` / `PHOTO_ZONE` / ... |

- PK: (`place_id`, `amenity_code`).
- 검색 필터 바텀시트(565:37178 등)에서 다중 선택 조건으로 사용.

### 2.5 `place_pet_policies` (허용 반려동물 정책)

| 컬럼 | 타입 | NOT NULL | 설명 |
|---|---|:---:|---|
| `place_id` | BIGINT | O | FK |
| `species` | VARCHAR(10) | O | `DOG` / `CAT` |
| `max_size` | VARCHAR(10) | O | `SMALL` / `MEDIUM` / `LARGE` — 이 크기까지 허용 |
| `requires_cage` | BOOLEAN | O | 케이지 필수 여부 |
| `indoor_allowed` | BOOLEAN | O | 실내 동반 가능 여부 |
| `extra_fee_krw` | INT | X | 반려동물 추가 요금 |
| `notes` | VARCHAR(300) | X | 추가 제한 사항 자유 텍스트 |

- PK: (`place_id`, `species`).
- 한 업체가 DOG 만 허용 / CAT 만 허용 / 둘 다 허용 등 자유 조합.

### 2.6 `place_category_meta` (카테고리별 특화 메타)

카테고리마다 의미 있는 속성 세트가 다르므로, **JSONB 컬럼** 또는 **전용 테이블**로 분리. MVP 는 JSONB 제안.

| 컬럼 | 타입 | NOT NULL | 설명 |
|---|---|:---:|---|
| `place_id` | BIGINT | O | PK, FK |
| `meta` | JSONB | O | 카테고리별 속성 (아래) |

카테고리별 JSON 스키마 예시:

```jsonc
// RESTAURANT / CAFE
{ "cuisineType": "한식", "hasKidsMenu": false }

// LODGING
{ "checkInTime": "15:00", "checkOutTime": "11:00",
  "roomTypes": ["스탠다드", "펫룸"], "petExtraFeeKrw": 20000 }

// KINDERGARTEN
{ "operatingHours": "평일 08:00~19:00",
  "acceptedSizes": ["SMALL","MEDIUM"],
  "requiresVaccination": true,
  "dailyRateKrw": 35000 }

// PARK
{ "offLeashArea": true, "waterArea": false,
  "size": "LARGE" }
```

> ❓ JSONB vs 카테고리별 테이블 분리는 운영 데이터 증가 추이에 따라 Phase 3+ 에서 재검토.

### 2.7 Enum 정의

```kotlin
enum class PlaceCategory { RESTAURANT, CAFE, KINDERGARTEN, LODGING, PARK }
enum class PlaceStatus { DRAFT, PUBLISHED, HIDDEN, CLOSED }
enum class AmenityCode {
    PARKING, TERRACE, INDOOR, OUTDOOR, FENCED, TREATS,
    WATER_BOWL, POOP_BAG, PLAYGROUND, PHOTO_ZONE
    // 확장 가능
}
```

### 2.8 상태 머신

```
   create        publish
  ┌──────┐    ┌──────────┐
  │DRAFT │───▶│PUBLISHED │◀───┐
  └──────┘    └─────┬────┘    │  restore
                    │         │
               hide │    ┌────┴────┐
                    ▼    │         │
               ┌────────┐│  CLOSED │
               │ HIDDEN │└─────────┘
               └────────┘      ▲
                    │close     │
                    └──────────┘
```

- `DRAFT → PUBLISHED`: 관리자 게시 액션.
- `PUBLISHED → HIDDEN`: 일시적 운영 이슈(신고 누적, 정보 오류 검토 중).
- `PUBLISHED|HIDDEN → CLOSED`: 폐업 확정.
- `CLOSED` 는 상세 페이지 접근 가능(폐업 뱃지 노출), **찜/리뷰 작성 불가** ([03-common-policies.md §3.3](./03-common-policies.md)).

### 2.9 제약 조건 & 인덱스

| 종류 | 표현 | 목적 |
|---|---|---|
| CHECK | `rating_avg BETWEEN 0 AND 5` | 집계 캐시 방어 |
| CHECK | `review_count >= 0`, `favorite_count >= 0` | |
| GIST | `CREATE INDEX idx_places_location ON places USING GIST (location)` | **반경 검색 핵심** |
| INDEX | `idx_places_status_category (status, category) WHERE deleted_at IS NULL` | 공개 목록 필터 |
| INDEX | `idx_places_name_trgm` (pg_trgm) | 부분일치 검색 (❓도입 여부 결정) |
| INDEX | `idx_places_favorite_count (favorite_count DESC) WHERE status='PUBLISHED'` | "요즘 찜 많은 장소" 보조 |
| INDEX | `idx_places_region (region_code) WHERE status='PUBLISHED'` | 지역 필터 |

---

## 3. 주요 기능 (Use Case)

### 3.1 업체 목록 / 지도 조회 (내 주변 · 카테고리별)

- **대상 화면**:
  [홈 내 주변](node-id=479:27003), [내 주변 리스트](node-id=479:27025),
  [내 주변 지도](node-id=479:27071), [내 주변 확장](node-id=565:46721),
  [식당 지도](node-id=522:36976), [식당 목록](node-id=522:37566),
  [로카유치원 지도](node-id=565:47221)

- **입력**:
  ```
  GET /api/v1/places
    ?category=RESTAURANT
    &lat=37.5665&lng=126.9780&radius=3000  (m)
    &sort=distance|latest|rating|popular
    &cursor=...&size=20
    &openNow=true
    &amenities=PARKING,TERRACE
    &petSpecies=DOG&petSize=SMALL
    &q=강남
  ```
- **동작**:
  - `status = 'PUBLISHED' AND deleted_at IS NULL` 고정.
  - 좌표 제공 시 `ST_DWithin(location, ST_MakePoint(lng,lat)::geography, radius)` 적용.
  - `sort=distance` 는 `ST_Distance` 오름차순 + `id DESC` 2차 정렬.
  - `openNow=true` 는 현재 시각(UTC → KST 변환 후) `place_hours` 에 매칭되는 업체만.
  - `petSpecies`/`petSize` 는 `place_pet_policies` 조건으로 필터.
- **응답 필드 (리스트 축약형)**: `id`, `name`, `category`, `address`, `lat`, `lng`, `distanceMeters?`, `thumbnailImageUrl`, `ratingAvg`, `reviewCount`, `favoriteCount`, `isFavorited`(로그인 시).
- **권한**: 비로그인 허용 (맞춤리뷰 맥락은 상세 진입 이후).

### 3.2 업체 상세 조회

- **대상 화면**:
  [업체 상세](node-id=480:50352), [상세 스크롤](node-id=479:24162), [상세 리뷰 섹션](node-id=479:24418)
- **입력**: `placeId`
- **출력**: 전체 필드 + 이미지 배열 + 영업시간 + 편의시설 + 동반 정책 + `rating_avg` + `review_count` + `isFavorited` (로그인 시) + 카테고리 메타(JSONB).
- **비즈니스 규칙**:
  - `PUBLISHED`/`CLOSED` 만 접근. `HIDDEN`/`DRAFT` 는 404 (관리자는 admin-api 에서 조회).
  - `CLOSED` 업체는 "폐업" 뱃지 필드를 포함(`isClosed: true`).
- **캐시**: `placeDetail::{placeId}` TTL 10분 (공통 정책 §7.2).
- **에러**: `PLACE_001` (404).

### 3.3 업체 검색 (키워드)

- **대상 화면**:
  [검색 진입](node-id=479:43541), [검색 결과](node-id=479:44981), [검색 결과 상세](node-id=480:48180),
  필터 바텀시트:
  [카테고리](node-id=565:37178), [지역](node-id=565:37241), [거리](node-id=565:37304),
  [허용 반려동물](node-id=565:37368), [편의시설](node-id=565:37432),
  [평점](node-id=565:37496), [영업중](node-id=565:37560)
- **입력**: `GET /api/v1/places/search?q=...&...(§3.1과 동일한 필터 파라미터)`
- **동작**:
  - 기본 검색: `name ILIKE '%q%' OR address ILIKE '%q%'` (MVP). pg_trgm 도입 시 `similarity` 정렬 옵션 추가.
  - 필터 조합은 §3.1 과 동일하며, 좌표 없는 경우 `sort=distance` 불가 → 400 에러.
- **권한**: 비로그인 허용.
- **❓ 확인 필요**: 검색어 미입력 + 필터만 적용한 요청은 `/places` 와 동일하게 응답할지, 전용 엔드포인트 분리할지.

### 3.4 추천 섹션 — 로카 추천 / 요즘 찜 많은

- **대상 화면**:
  [홈](node-id=479:26692), [홈 헤더](node-id=479:42172),
  [요즘 찜 많은 장소](node-id=479:26910), [로카 추천 장소](node-id=479:26933)
- **동작**:
  - **로카 추천 장소**: 관리자 큐레이션 — 별도 관리 테이블(`place_curations`) 또는 Phase 2 에선 `places.is_curated_pick` boolean + `curation_order` 로 단순화 제안.
  - **요즘 찜 많은 장소**: 최근 7일간 신규 찜 수 상위 N개. 집계는 Redis ZSet(`wishlist:trending:7d`) 또는 배치 테이블. [07-wishlist-spec.md §8](./07-wishlist-spec.md) 참조.
- **권한**: 비로그인 허용.

### 3.5 업체 이미지 · 영업시간 · 편의시설 조회

- 상세 응답에 포함되므로 별도 엔드포인트는 MVP 에서 미제공.
- 이미지 배열은 `display_order ASC` 정렬.

### 3.6 비즈니스 액션 (전화 / 카카오톡 / 이메일 / 예약 / 신고)

- **대상 팝업**:
  [카톡 채널](node-id=479:24707), [전화](node-id=603:42166),
  [이메일](node-id=479:25381), [예약](node-id=489:54774),
  [신고](node-id=489:56526)
- **서버 책임**: 업체 상세 응답에 `phone`, `kakaoChannelId`, `email`, `website`(예약 링크) 필드를 포함만 함. **실제 다이얼/앱 딥링크는 클라이언트 책임**.
- **신고**는 Support(D12) 도메인에서 처리. `reports (target_type=PLACE, target_id=placeId, reason_code)` 형태. 이 문서 범위 밖.

### 3.7 관리자 — 업체 CRUD

- admin-api 전용.
- `POST /admin/api/v1/places` — DRAFT 생성.
- `PATCH /admin/api/v1/places/{id}` — 정보 수정 → 캐시 무효화.
- `POST /admin/api/v1/places/{id}/publish` / `hide` / `close` — 상태 전환.
- `DELETE /admin/api/v1/places/{id}` — 소프트 삭제.
- 이미지/편의시설/영업시간/동반 정책/카테고리 메타 모두 서브 엔드포인트로 관리.
- **권한**: `MemberRole.ADMIN` + admin-api 인증 ([03-common-policies.md §9](./03-common-policies.md) 참조).
- **상세 구현은 Phase 4 운영 안정화 문서에서 확장.**

---

## 4. 비즈니스 규칙 & 제약

### 4.1 도메인 불변식

1. **비로그인도 `PUBLISHED` 업체 조회 가능**. `HIDDEN`/`DRAFT` 는 접근 불가.
2. **좌표 필수** — `location` 은 NOT NULL. 생성 시 주소 지오코딩 또는 수동 입력.
3. **rating_avg / review_count / favorite_count 은 비정규화 캐시** — 트랜잭션 내에서 Review/Favorite 변경 시 동반 갱신. 불일치 방지를 위해 **야간 배치 재계산** 권장.
4. **CLOSED 업체는 찜/리뷰 작성 불가.** 기존 찜/리뷰는 유지.
5. **한 업체의 `place_pet_policies` 는 Species 당 1 row**. 중복 불가.

### 4.2 입력 제약

| 필드 | 제약 |
|---|---|
| `name` | 1~100자 |
| `description` | 최대 3000자 |
| `address` | 1~255자. 주소 검증/지오코딩은 관리자 입력 단계에서 완료 |
| `lat` | -90 ~ 90 |
| `lng` | -180 ~ 180 |
| `radius` (검색) | 100m ~ 20000m |
| 이미지 | 업체당 최대 20장 |
| 편의시설 태그 | 업체당 최대 20개 |

### 4.3 좌표계 & 지도 Provider

- **좌표계**: WGS84 (`SRID 4326`). 네이버 지도 SDK 기본 호환.
- **렌더링**: 클라이언트(네이버 지도) 책임. 서버는 `lat`/`lng` 만 제공.
- **거리 계산**: PostGIS `geography` 타입의 `ST_Distance` / `ST_DWithin` 사용 (m 단위 직접 반환).

---

## 5. API 엔드포인트 초안 (REST)

### 5.1 공개 (app-api)

| Method | Path | Auth | 설명 |
|---|---|:---:|---|
| GET | `/api/v1/places` | optional | 업체 목록 (카테고리/좌표/필터/정렬) |
| GET | `/api/v1/places/search` | optional | 키워드 + 필터 검색 |
| GET | `/api/v1/places/{placeId}` | optional | 상세 |
| GET | `/api/v1/places/curated` | optional | 로카 추천 장소 섹션 |
| GET | `/api/v1/places/trending` | optional | 요즘 찜 많은 장소 |
| GET | `/api/v1/places/nearby` | optional | `/places?sort=distance` 의 편의 별칭 (필수 lat/lng) |

> `GET /api/v1/places/{id}/reviews` 는 [06-review-spec.md](./06-review-spec.md) 에서 정의.
> `POST /api/v1/places/{id}/favorite` 는 [07-wishlist-spec.md](./07-wishlist-spec.md) 에서 정의.

### 5.2 관리자 (admin-api) — 스펙 간략

| Method | Path | 설명 |
|---|---|---|
| POST | `/admin/api/v1/places` | 등록(DRAFT) |
| PATCH | `/admin/api/v1/places/{id}` | 수정 |
| POST | `/admin/api/v1/places/{id}/publish` | PUBLISHED 전환 |
| POST | `/admin/api/v1/places/{id}/hide` | HIDDEN 전환 |
| POST | `/admin/api/v1/places/{id}/close` | CLOSED 전환 |
| DELETE | `/admin/api/v1/places/{id}` | 소프트 삭제 |
| POST | `/admin/api/v1/places/{id}/images` | 이미지 등록 (Presigned 후 COMMIT) |
| PUT | `/admin/api/v1/places/{id}/hours` | 영업시간 일괄 치환 |
| PUT | `/admin/api/v1/places/{id}/amenities` | 편의시설 일괄 치환 |
| PUT | `/admin/api/v1/places/{id}/pet-policies` | 동반 정책 일괄 치환 |
| PUT | `/admin/api/v1/places/{id}/category-meta` | 카테고리 메타 교체 |

### 5.3 응답 스키마 (상세 — 축약)

```json
{
  "id": 42,
  "name": "강아지와 식당",
  "category": "RESTAURANT",
  "status": "PUBLISHED",
  "address": "서울시 강남구 테헤란로 123",
  "roadAddress": "서울시 강남구 테헤란로 123",
  "regionCode": "11680",
  "location": { "lat": 37.5013, "lng": 127.0394 },
  "distanceMeters": 321,
  "phone": "02-1234-5678",
  "website": "https://booking.example.com/...",
  "kakaoChannelId": "_abcdef",
  "email": "hello@example.com",
  "description": "반려견 동반 가능한 한식당...",
  "thumbnailImageUrl": "https://cdn.locapet.app/places/42/thumb.jpg",
  "images": [
    { "id": 101, "imageUrl": "...", "displayOrder": 0 }
  ],
  "hours": [
    { "dayOfWeek": 1, "openTime": "11:00", "closeTime": "22:00", "isClosed": false, "note": null }
  ],
  "amenities": ["PARKING", "TERRACE", "WATER_BOWL"],
  "petPolicies": [
    { "species": "DOG", "maxSize": "MEDIUM", "requiresCage": false, "indoorAllowed": true, "extraFeeKrw": 0, "notes": null }
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

---

## 6. 에러 코드

| 코드 | HTTP | 메시지 | 발생 상황 |
|---|:---:|---|---|
| `PLACE_001` | 404 | 해당 업체를 찾을 수 없어요. | 없는 id, HIDDEN/DRAFT, 삭제됨 |
| `PLACE_002` | 400 | 검색 조건을 확인해주세요. | lat/lng 한쪽만, radius 범위 밖 등 |
| `PLACE_003` | 400 | 지원하지 않는 카테고리예요. | Enum 외 값 |
| `PLACE_004` | 409 | 이미 폐업된 업체에는 이 작업을 할 수 없어요. | CLOSED 업체 찜/리뷰 시도 |
| `PLACE_005` | 400 | 좌표 정렬은 위치 정보가 있어야 해요. | sort=distance 인데 lat/lng 없음 |
| `PLACE_006` | 403 | 관리자만 이 작업을 할 수 있어요. | 일반 회원이 admin 엔드포인트 접근 |
| `PLACE_007` | 409 | 같은 종의 동반 정책이 이미 등록되어 있어요. | `(place_id, species)` UNIQUE 위반 |
| `PLACE_008` | 400 | 좌표 값이 유효하지 않아요. | lat/lng 범위 밖 |

---

## 7. 엣지 케이스 & 예외 처리

| 케이스 | 처리 |
|---|---|
| **좌표 없이 "내 주변" 진입** | 클라이언트가 위치 권한 요청. 서버는 `lat/lng` 없으면 `/places/nearby` 호출 시 `PLACE_005` 반환. |
| **CLOSED 업체 리뷰 열람** | 허용 — 과거 기록이므로. |
| **CLOSED 업체 찜 클릭** | 409 + `PLACE_004`. 클라이언트는 "폐업한 업체입니다" 토스트. |
| **반경 검색 결과 0건** | `items: []` + `hasNext: false`. 클라이언트는 "주변에 업체가 없어요" Empty. |
| **동명 업체 여러개** | 중복 데이터 허용(주소로 구분). 관리자 검수 단계에서 dedupe. |
| **영업시간 익일 새벽 종료(24시 이후)** | ❓결정 필요: `close_time=25:30` 또는 `is_next_day=true` 플래그. 현재는 클라이언트 해석 책임. |
| **대표 이미지 부재** | `thumbnail_image_url IS NULL` 시 카테고리별 기본 이미지를 **클라이언트**가 fallback 렌더. |
| **신고 누적 자동 HIDDEN** | [03-common-policies.md §3.2](./03-common-policies.md) — Report 3건 이상 시 자동 HIDDEN 제안(정책 확정 ❓). |
| **동시성 — 찜 수 캐시 갱신** | `UPDATE places SET favorite_count = favorite_count + 1` 원자 연산. 음수 방어 CHECK. |
| **검색 q 내 특수문자** | 서버에서 escape. SQL Injection 방어는 파라미터 바인딩으로 기본 처리. |

---

## 8. 성능 / 인덱싱 고려사항

### 8.1 주요 쿼리 패턴

| 패턴 | 빈도 | 인덱스 / 전략 |
|---|:---:|---|
| 반경 검색 (PostGIS) | **매우 높음** | GIST `idx_places_location` + `status` 조건 |
| 카테고리 + 지역 필터 | 높음 | `idx_places_status_category` + `idx_places_region` |
| 상세 단건 조회 | 매우 높음 | PK + 캐시 (`placeDetail::{id}`) |
| 요즘 찜 많은 | 중간 | `idx_places_favorite_count` or Redis ZSet |
| 키워드 LIKE | 중간 | pg_trgm 도입 시 성능 개선 ❓ |

### 8.2 캐시

| 캐시 | 키 | TTL | 무효화 |
|---|---|---|---|
| `placeDetail` | `{placeId}` | 10분 | 수정/상태전환/이미지·정책 변경 시 |
| `placesCurated` | 단일 | 10분 | 관리자 큐레이션 변경 시 |
| `placesTrending` | `7d` | 10분 | 배치 갱신 주기에 맞춤 (15분 등) |
| `placeCategories` | 단일 | 1시간 | (Enum이므로 사실상 불변) |

### 8.3 집계 캐시 컬럼 갱신 전략

- `rating_avg` / `review_count`: Review 작성/수정/삭제 시 **동일 트랜잭션**에서 `UPDATE places SET ... WHERE id = ?`.
- `favorite_count`: Wishlist 추가/제거 시 **동일 트랜잭션**에서 갱신.
- 야간 배치(`02:00 KST`)로 실제 값과 재동기화 — 드리프트 보정.

### 8.4 대용량 목록 응답

- Cursor 방식 (공통 정책 §4.1).
- 지도 바운딩 박스 검색(선택): `ST_MakeEnvelope(minLng, minLat, maxLng, maxLat, 4326)` — 지도 뷰포트 내 포인트 조회용 전용 엔드포인트 고려(Phase 3).

---

## 9. 의존성 & 향후 확장

### 9.1 의존 도메인

- **Review / Favorite** — 집계 캐시 피드백.
- **Report** — 업체 신고 처리.
- **Upload** — Presigned 이미지 업로드.
- **Meta** — 카테고리/편의시설 코드표를 향후 DB 마스터로 이동 가능.

### 9.2 확장 포인트

| 확장 | Phase | 비고 |
|---|:---:|---|
| 카테고리 계층화 (식당 > 한식 등) | Phase 3+ | 현재는 5개 고정, 계층 없음 |
| 업체 오너 파트너 기능 | Phase 5+ | `owner_member_id` 활용, 답글/정보 셀프 수정 |
| 예약 시스템 자체 구현 | Out of Scope | 외부 링크 유지 |
| 스펠링 / 동의어 검색, Elasticsearch | Phase 4+ | 트래픽 축적 후 |
| 지도 클러스터링 서버 지원 | Phase 3+ | 줌 레벨별 H3/geohash 집계 |
| 다국어 업체명 | Phase 5+ | `name_ko`, `name_en` 분리 |

### 9.3 ❓ 확인 필요 항목

1. **영업시간 자정 넘김 표현** — `25:30` vs `is_next_day`.
2. **비로그인 업체 상세 전화번호 노출 범위** — 개인정보 차원 ([03-common-policies.md §1.3](./03-common-policies.md)).
3. **로카 추천 큐레이션 저장 구조** — boolean 컬럼 vs 별도 `place_curations` 테이블.
4. **신고 누적 자동 HIDDEN 임계값** — 3건? 5건? 운영 축적 후 결정.
5. **`openNow` 필터 구현 기준 타임존** — KST 고정 vs 클라이언트 TZ 파라미터.
6. **검색 pg_trgm / Elasticsearch 도입 시점** — 데이터 10만건 넘어가면 필수.
7. **카테고리 메타 JSONB vs 전용 테이블** — 초기 JSONB, 운영 중 스키마 고정 시 이관.
