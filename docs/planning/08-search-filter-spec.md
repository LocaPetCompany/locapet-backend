# 08. 검색 & 저장된 필터(Search & Saved Filter) 기능 명세

> 본 문서는 로카펫의 **D7. 검색/필터(Search)** 도메인 상세 기획이다.
> 상위 문서: [01. 도메인 맵](./01-domain-map.md), [02. 용어집](./02-glossary.md), [03. 공통 정책](./03-common-policies.md)
> 연관 문서: [05. 업체(Place)](./05-place-spec.md) — 검색 필터는 Place 조회를 파라미터화한 뷰이다.

---

## 1. 개요

### 1.1 도메인 책임

- **업체 검색** — 키워드 · 카테고리 · 좌표 · 허용 반려동물 · 편의시설 · 평점 · 영업중 등 **복합 필터** 기반 Place 조회 API 를 제공한다. 실제 데이터 원본은 Place 도메인이며, Search 도메인은 **필터 조합과 저장 기능**을 책임진다.
- **저장된 필터(Saved Filter)** — 회원이 자주 쓰는 필터 조합을 이름 붙여 저장하고, 재적용 · 수정 · 삭제할 수 있다.
- **검색 기록(Search History)** — (Phase 4+) 최근 검색어를 서버에 저장하여 자동완성/재검색 지원.

### 1.2 다른 도메인과의 관계

| 방향 | 대상 도메인 | 관계 |
|---|---|---|
| 참조 | Member(D3) | `saved_filters.member_id` → FK |
| 호출 | Place(D5) | 필터 파라미터로 `/places`, `/places/search` 공개 API 호출 |
| 호출 | Pet(D4) | 저장 필터의 `species`/`size` 기본값 프리셋에 대표 Pet 활용 (클라이언트 책임) |
| 피드백 | Feed(D6) | (Phase 3+) "저장된 필터" 가 홈 섹션 단축 진입으로 활용 가능 |

### 1.3 본 명세의 경계

- **검색 쿼리 자체는 무상태(stateless)** — `GET /api/v1/places/search?...` 는 Place 도메인 엔드포인트이며, 본 문서는 **필터 조합의 규격 · 저장 · 적용**에 집중한다.
- 좌표 기반 공간 검색의 PostGIS 쿼리 패턴은 [05-place-spec.md §3.1, §8.1](./05-place-spec.md) 에서 정의한다. 본 문서는 거기에 덧붙는 **필터 계약**과 **저장 모델**만 다룬다.

---

## 2. 엔티티 & 데이터 모델

### 2.1 `saved_filters`

| 컬럼 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `id` | BIGSERIAL | O | - | PK |
| `member_id` | BIGINT | O | - | FK → members.id |
| `name` | VARCHAR(30) | O | - | 회원이 붙인 이름. 1~30자 |
| `filter_json` | JSONB | O | - | 필터 조건 직렬화 (§2.4) |
| `last_applied_at` | TIMESTAMPTZ | X | NULL | 마지막 적용 시각 (정렬·노출 우선순위용) |
| `created_at` | TIMESTAMPTZ | O | NOW() | |
| `updated_at` | TIMESTAMPTZ | O | NOW() | |
| `deleted_at` | TIMESTAMPTZ | X | NULL | 소프트 삭제 마커 |

### 2.2 제약 조건 & 인덱스

| 종류 | 표현 | 목적 |
|---|---|---|
| FK | `member_id → members(id)` | 소유권 |
| CHECK | `char_length(name) BETWEEN 1 AND 30` | |
| UNIQUE (부분) | `CREATE UNIQUE INDEX uk_saved_filters_member_name_active ON saved_filters (member_id, name) WHERE deleted_at IS NULL` | **회원당 활성 이름 중복 방지** |
| INDEX | `idx_saved_filters_member_active (member_id, created_at DESC) WHERE deleted_at IS NULL` | 내 저장 필터 목록 |
| INDEX | `idx_saved_filters_member_last_applied (member_id, last_applied_at DESC NULLS LAST) WHERE deleted_at IS NULL` | "최근 사용한 필터" 정렬 |

### 2.3 `SearchFilterSort` Enum (API 파라미터)

```kotlin
enum class SavedFilterSort {
    CREATED_AT_DESC,    // 최신 저장순 (기본)
    CREATED_AT_ASC,     // 오래된 저장순
    NAME_ASC,           // 이름 가나다순
    LAST_APPLIED_DESC   // 최근 사용순
}
```

### 2.4 `filter_json` 스키마 (JSONB)

단일 통합 필터 바텀시트(Figma `565:37178`) 가 수집하는 모든 축을 담는다. 모든 필드는 **선택(optional)** — 누락 시 "제한 없음"으로 해석한다.

```jsonc
{
  // 1. 카테고리 (Place 5종)
  "categories": ["RESTAURANT", "CAFE"],

  // 2. 허용 반려동물 (종 + 크기)
  "petFilter": {
    "species": ["DOG", "CAT"],               // 빈 배열/null = 전체
    "sizes": ["SMALL", "MEDIUM"],            // 빈 배열/null = 전체
    "weightKgMin": 1,                        // 슬라이더 최솟값 (Pet 기준)
    "weightKgMax": 35                        // 슬라이더 최댓값 (35 = 제한 없음 sentinel)
  },

  // 3. 반려동물 조건 chips (예방접종·중성화·동물등록칩 — MVP 는 Pet 측 속성 매칭용 메타 / 업체 측 요구사항 여부)
  "petRequirements": ["NEUTERED", "VACCINATED", "REGISTERED_CHIP"],

  // 4. 편의시설 (AmenityCode)
  "amenities": ["PARKING", "FENCED", "TERRACE"],

  // 5. 제한 여부 chips (Place 허용 조건)
  "restrictions": {
    "indoorAllowed": true,        // 실내 가능
    "outdoorAllowed": true,       // 실외 가능
    "dangerousDogAllowed": false, // 맹견 가능
    "cageFree": true              // 케이지 불필요
  },

  // 6. 평점 최소값
  "minRating": 4.0,

  // 7. 영업중
  "openNow": true,

  // 8. 좌표 (선택)
  "location": {
    "lat": 37.5665,
    "lng": 126.9780,
    "radiusMeters": 3000
  },

  // 9. 키워드
  "keyword": "강남",

  // 10. 정렬 (저장 시점의 정렬도 함께 기억)
  "sort": "distance"   // distance | latest | rating | popular
}
```

- **확장 원칙**: `filter_json` 은 **unknown 필드 무시(ignore unknown)**. 서버는 알려진 키만 해석하고 나머지는 보존한다. 새 필드 추가 시 하위 호환 유지.
- **버전 관리**: 스키마 breaking change 시 `{ "_v": 2, ... }` 형태로 버전 필드를 도입.

### 2.5 상태 머신

`saved_filters` 는 명시적 상태 없이 `deleted_at` 으로만 분기한다 ([04-pet-spec.md §2.5](./04-pet-spec.md) 와 동일 패턴).

```
  ┌──────────┐   delete    ┌───────────┐
  │  ACTIVE  │────────────▶│  DELETED  │
  └──────────┘             └───────────┘
        ▲                         │
        │   (복구 MVP 범위 밖)    │
        └─────────────────────────┘
```

---

## 3. 주요 기능 (Use Case)

### 3.1 업체 검색 (키워드 + 필터)

- **대상 화면**:
  [검색 전](node-id=479:44981), [검색 후](node-id=479:43541), [저장 필터 적용 후 결과](node-id=479:44853),
  [검색 결과 상세 1](node-id=480:48180), [검색 결과 상세 7](node-id=479:43567),
  [검색 필터 바텀시트 (통합)](node-id=565:37178), [개별 축 바텀시트 1](node-id=565:37241), [2](node-id=565:37304), [3](node-id=565:37368), [4](node-id=565:37432), [5](node-id=565:37496), [6](node-id=565:37560),
  [필터 적용 결과](node-id=479:44710)

- **엔드포인트** (Place 도메인 재사용):
  - `GET /api/v1/places/search` — 복합 검색
  - `GET /api/v1/places` — 필터만 (키워드 없음)

- **입력 (쿼리 파라미터)**:
  ```
  GET /api/v1/places/search
    ?q=강남
    &categories=RESTAURANT,CAFE
    &species=DOG&sizes=SMALL,MEDIUM
    &amenities=PARKING,FENCED
    &indoorAllowed=true&cageFree=true
    &minRating=4.0
    &openNow=true
    &lat=37.5665&lng=126.9780&radius=3000
    &sort=distance
    &cursor=...&size=20
  ```

- **필터 해석 규칙**:
  - 배열 파라미터는 **OR** 결합 (예: `categories=RESTAURANT,CAFE` → `category IN ('RESTAURANT','CAFE')`).
  - 서로 다른 필터 축은 **AND** 결합.
  - `species` + `sizes` 는 `place_pet_policies` 조인 후 필터 — 한 업체의 정책 중 하나라도 매칭되면 통과.
  - `openNow=true` 는 **KST 기준 현재 시각** 으로 `place_hours` 매칭 ([05-place-spec.md §9.3](./05-place-spec.md)).
  - `sort=distance` 는 `lat`/`lng` 필수 — 없으면 `PLACE_005` (400).

- **저장된 필터 적용**:
  - `GET /api/v1/places/search?savedFilterId=42&lat=...&lng=...` — 서버에서 `filter_json` 을 로드하여 쿼리 파라미터로 전개. 좌표/키워드 등 **재실행 시마다 달라지는 값은 쿼리 파라미터가 우선** (overlay).

- **사후조건** (저장 필터 적용 시): `saved_filters.last_applied_at = NOW()` 갱신.

- **권한**: 비로그인 허용. 단, `savedFilterId` 사용은 본인 소유 필터만 (`SEARCH_004` 403).

- **응답**: Place 도메인의 목록 응답 포맷 재사용 ([05-place-spec.md §3.1](./05-place-spec.md)).

### 3.2 저장된 필터 생성

- **대상 화면**:
  [저장하기 진입](node-id=479:44304), [저장 입력 완료](node-id=479:44573),
  [저장 플로우 A](node-id=565:38081), [저장 입력 완료 B](node-id=565:38218),
  [검색 필터 바텀시트 상단의 "저장" 버튼 진입](node-id=565:37178)

- **엔드포인트**: `POST /api/v1/saved-filters`

- **사전조건**: 로그인 (`COMPLETED`), 저장된 필터 **최대 20개** 미만.

- **입력**:
  ```json
  {
    "name": "강남 한식 소형견",
    "filter": {
      "categories": ["RESTAURANT"],
      "petFilter": { "species": ["DOG"], "sizes": ["SMALL"] },
      "amenities": ["PARKING", "TERRACE"],
      "minRating": 4.0,
      "openNow": false
    }
  }
  ```

- **비즈니스 규칙**:
  - `name` 1~30자, trim 후 연속 공백 1칸 축소.
  - 이름 중복 검사 — 회원당 활성 필터 내 `name` UNIQUE. 중복 시 `SEARCH_002` (409).
  - `filter_json` 스키마 검증 — 카테고리/Enum 값, 좌표 범위, `minRating` 0~5, `radiusMeters` 100~20000 등.
  - 빈 필터(`{}`) 저장 허용 여부 ❓ — **MVP 제안: 최소 1개 필드 이상 필수** → `SEARCH_005`.

- **에러**:

| 상황 | 코드 | HTTP |
|---|---|:---:|
| 이름 중복 | `SEARCH_002` | 409 |
| 최대 개수 초과 (20) | `SEARCH_003` | 409 |
| 필터 스키마 불일치 | `SEARCH_001` | 400 |
| 빈 필터 | `SEARCH_005` | 400 |

### 3.3 저장된 필터 목록 조회

- **대상 화면**:
  [저장된 필터 목록](node-id=479:43756),
  [저장된 필터 Empty](node-id=479:44035),
  [저장 필터 바텀시트 목록 A](node-id=565:37098), [B](node-id=565:37130), [C](node-id=610:48257)

- **엔드포인트**: `GET /api/v1/saved-filters?sort=CREATED_AT_DESC&size=20`
- **응답 아이템**:
  ```json
  {
    "id": 42,
    "name": "강남 한식 소형견",
    "filter": { "...": "..." },
    "summaryText": "식당 · 강아지(소형) · 주차장, 테라스",
    "lastAppliedAt": "2026-04-17T11:00:00Z",
    "createdAt": "2026-03-10T08:00:00Z"
  }
  ```
- **`summaryText` 생성**: 서버가 `filter_json` 을 사람이 읽을 한 줄 요약으로 만들어 함께 전달 (UI 편의). 없어도 클라이언트에서 재구성 가능.
- **정렬**: `CREATED_AT_DESC`(기본), `NAME_ASC`, `LAST_APPLIED_DESC`.
- **페이지네이션**: Cursor 기반 (공통 정책 §4.1). 최대 20개 제약이므로 사실상 1페이지.
- **권한**: accessToken 필수. 본인 것만.

### 3.4 저장된 필터 적용

- **대상 화면**:
  [적용 UI](node-id=479:43895),
  [적용 후 결과 리스트](node-id=479:44853),
  [적용 진입 (필터 바텀시트의 적용 탭)](node-id=479:44710)

- **적용 방식 2종**:
  1. **간접 적용** — `GET /api/v1/places/search?savedFilterId={id}&...overlay params` (§3.1). 권장 방식.
  2. **직접 조회** — `GET /api/v1/saved-filters/{id}` 로 `filter_json` 을 받아 클라이언트가 쿼리 파라미터로 풀어 재호출. 복잡 UI 에 유리.

- **사후조건**: `last_applied_at = NOW()` 갱신 (양쪽 방식 동일).

- **에러**:

| 상황 | 코드 | HTTP |
|---|---|:---:|
| 존재하지 않는 필터 | `SEARCH_006` | 404 |
| 타인 소유 필터 | `SEARCH_004` | 403 |

### 3.5 저장된 필터 수정

- **엔드포인트**: `PATCH /api/v1/saved-filters/{id}`
- **입력**: `name` 또는 `filter` 부분 업데이트. 둘 다 미지정 시 400.
- **규칙**: 이름 변경 시 중복 검사 재수행.
- **권한**: 본인 소유만.

### 3.6 저장된 필터 삭제

- **대상 화면**: [삭제 확인](node-id=603:41728), [삭제 편집 모드](node-id=479:44441)
- **단건 삭제**: `DELETE /api/v1/saved-filters/{id}`
- **다건 일괄 삭제**: `POST /api/v1/saved-filters/delete { "ids": [1,2,3] }` — Figma 편집 모드 지원.
- **동작**: 소프트 삭제 (`deleted_at = NOW()`). 해당 row 는 UNIQUE 부분 인덱스에서 빠져 동일 이름으로 재생성 가능.
- **권한**: 본인 소유만.

### 3.7 검색 (키워드 히스토리 / 자동완성) — Phase 4+

- **대상 화면**: [검색 진입(빈 상태)](node-id=479:44981)
- **MVP 범위 밖**. 현재는 빈 검색 진입 시 "검색어를 입력해주세요" 안내.
- 향후 `search_history (member_id, keyword, searched_at)` 테이블 + 인기 검색어 Redis ZSet 도입 검토.

---

## 4. 비즈니스 규칙 & 제약

### 4.1 도메인 불변식

1. **회원당 저장 필터 최대 20개**. 초과 시 `SEARCH_003` — 기존 필터 삭제 유도.
2. **회원당 이름은 활성 내 UNIQUE** — 소프트 삭제된 이름은 재사용 가능.
3. **`filter_json` 은 unknown 필드 무시** — 서버 배포 선후 관계에 무관하게 안전.
4. **저장 필터 적용 시 overlay 파라미터가 우선** (좌표/키워드 등).
5. **빈 필터 저장 금지** — 최소 1개 축은 있어야 의미 있음.
6. **검색 API 는 비로그인 허용**, 단 `savedFilterId` 는 로그인 + 본인 소유.

### 4.2 입력 제약

| 필드 | 제약 |
|---|---|
| `name` | 1~30자, trim/축소, 개행 금지 |
| `filter.categories` | 최대 5개 (Place 카테고리 수) |
| `filter.amenities` | 최대 20개 |
| `filter.minRating` | 0.0 ~ 5.0, 소수 1자리 |
| `filter.location.radiusMeters` | 100 ~ 20000 |
| `filter_json` 직렬화 크기 | 8KB 이하 권장 (JSONB 저장 한계는 크나 성능 관점) |
| `q` (키워드) | 1~50자, 앞뒤 trim, 연속 공백 1칸 축소 |
| `ids` (일괄 삭제) | 1~50개 |

### 4.3 필터 ↔ Place 조인 매트릭스

| 필터 키 | 대상 테이블 | 조인/조건 |
|---|---|---|
| `categories` | `places` | `places.category IN (...)` |
| `petFilter.species` | `place_pet_policies` | `EXISTS (SELECT 1 FROM place_pet_policies pp WHERE pp.place_id = p.id AND pp.species IN (...))` |
| `petFilter.sizes` | `place_pet_policies` | 같은 EXISTS 서브쿼리 + `pp.max_size IN (...)` |
| `amenities` | `place_amenities` | 다중 AND: 요청 태그 전부 포함 (모든 태그 만족 업체). ❓ OR 로 바꿀지 정책 결정 필요 |
| `restrictions.indoorAllowed` | `place_pet_policies` | `pp.indoor_allowed = true` |
| `restrictions.cageFree` | `place_pet_policies` | `pp.requires_cage = false` |
| `minRating` | `places` | `p.rating_avg >= :minRating` |
| `openNow` | `place_hours` | KST 변환 시각으로 매칭 서브쿼리 |
| `location` | `places.location` | `ST_DWithin(p.location, ST_MakePoint(lng,lat)::geography, radius)` |
| `q` | `places` | `p.name ILIKE '%q%' OR p.address ILIKE '%q%'` (MVP) |

### 4.4 SQL 샘플 (복합 검색)

```sql
SELECT p.id, p.name, p.category, p.address,
       ST_Y(p.location::geometry) AS lat,
       ST_X(p.location::geometry) AS lng,
       ST_Distance(p.location, ST_MakePoint(:lng, :lat)::geography) AS distance_m,
       p.rating_avg, p.review_count, p.favorite_count
  FROM places p
 WHERE p.status = 'PUBLISHED'
   AND p.deleted_at IS NULL
   AND p.category = ANY(:categories)
   AND (:minRating IS NULL OR p.rating_avg >= :minRating)
   AND ST_DWithin(p.location, ST_MakePoint(:lng, :lat)::geography, :radius)
   AND (p.name ILIKE '%' || :q || '%' OR p.address ILIKE '%' || :q || '%')
   AND EXISTS (
       SELECT 1 FROM place_pet_policies pp
        WHERE pp.place_id = p.id
          AND pp.species = ANY(:species)
          AND pp.max_size = ANY(:sizes)
          AND (:cageFree IS NULL OR pp.requires_cage = false)
   )
   AND NOT EXISTS (
       SELECT 1 FROM unnest(:amenities::text[]) AS a(code)
        WHERE NOT EXISTS (
            SELECT 1 FROM place_amenities pa
             WHERE pa.place_id = p.id AND pa.amenity_code = a.code
        )
   )
 ORDER BY distance_m ASC, p.id DESC
 LIMIT :size + 1;
```

> `amenities` 는 "요청 태그 **모두** 포함" 시 매칭 — 위 `NOT EXISTS ... NOT EXISTS` 이중 부정 패턴. OR 로 바꿀 경우 `EXISTS` 1개 + `IN (...)`.

---

## 5. API 엔드포인트 초안 (REST)

### 5.1 검색 (Place 도메인 재사용)

| Method | Path | Auth | 설명 |
|---|---|:---:|---|
| GET | `/api/v1/places/search` | optional | 키워드 + 필터 검색 |
| GET | `/api/v1/places` | optional | 필터만 (키워드 없음) |
| GET | `/api/v1/places/nearby` | optional | `sort=distance` 편의 별칭 |

→ 상세 스펙은 [05-place-spec.md §3.1, §5.1](./05-place-spec.md).

### 5.2 저장된 필터

| Method | Path | Auth | 설명 |
|---|---|:---:|---|
| POST | `/api/v1/saved-filters` | accessToken | 생성 |
| GET | `/api/v1/saved-filters` | accessToken | 내 목록 |
| GET | `/api/v1/saved-filters/{id}` | accessToken | 단건 조회 |
| PATCH | `/api/v1/saved-filters/{id}` | accessToken | 수정 (이름/필터) |
| DELETE | `/api/v1/saved-filters/{id}` | accessToken | 단건 삭제 |
| POST | `/api/v1/saved-filters/delete` | accessToken | 일괄 삭제 |
| POST | `/api/v1/saved-filters/{id}/apply-log` | accessToken | 적용 로그만 갱신 (선택 — 간접 적용 경로에서 쓸지 결정 ❓) |

### 5.3 응답 스키마

#### 생성 / 단건 조회 응답

```json
{
  "id": 42,
  "name": "강남 한식 소형견",
  "filter": {
    "categories": ["RESTAURANT"],
    "petFilter": { "species": ["DOG"], "sizes": ["SMALL"] },
    "amenities": ["PARKING", "TERRACE"],
    "minRating": 4.0
  },
  "summaryText": "식당 · 강아지(소형) · 주차장, 테라스 · 평점 4.0↑",
  "lastAppliedAt": null,
  "createdAt": "2026-04-18T09:30:00Z",
  "updatedAt": "2026-04-18T09:30:00Z"
}
```

#### 목록 응답

```json
{
  "items": [ /* 위 스키마 축약 */ ],
  "totalCount": 7,
  "maxCount": 20,
  "nextCursor": null,
  "hasNext": false
}
```

---

## 6. 에러 코드

`03-common-policies.md §6.2` 컨벤션. `SEARCH_{NNN}`.

| 코드 | HTTP | 메시지 | 발생 상황 |
|---|:---:|---|---|
| `SEARCH_001` | 400 | 검색 필터 조건을 확인해주세요. | `filter_json` 스키마 위반, Enum 외 값 |
| `SEARCH_002` | 409 | 같은 이름의 저장 필터가 이미 있어요. | 회원 × `name` UNIQUE 위반 |
| `SEARCH_003` | 409 | 저장 필터는 최대 20개까지 만들 수 있어요. | 개수 초과 |
| `SEARCH_004` | 403 | 본인의 저장 필터만 사용할 수 있어요. | 타인 소유 접근 |
| `SEARCH_005` | 400 | 저장 필터에는 최소 1개 조건이 필요해요. | 빈 필터 저장 시도 |
| `SEARCH_006` | 404 | 저장 필터를 찾을 수 없어요. | 없는 id, 이미 삭제됨 |
| `SEARCH_007` | 400 | 검색어는 50자 이하로 입력해주세요. | `q` 길이 초과 |
| `PLACE_002` | 400 | 검색 조건을 확인해주세요. | lat/lng 한쪽만, radius 범위 밖 등 (Place 도메인 공유) |
| `PLACE_005` | 400 | 좌표 정렬은 위치 정보가 있어야 해요. | `sort=distance` 인데 lat/lng 없음 |

---

## 7. 엣지 케이스 & 예외 처리

| 케이스 | 처리 |
|---|---|
| **저장 필터 적용 시 좌표가 필요한데 없음** | `sort=distance` 포함 저장 → overlay 좌표 미지정 → `PLACE_005` 400 + UX 상 위치 권한 재요청. |
| **저장된 필터의 카테고리가 Place Enum 에서 제거됨** | 서버 해석 시 알려진 값만 사용 (unknown drop). `summaryText` 는 재계산하여 일관성 유지. |
| **저장된 필터 이름에 이모지/특수문자** | name 은 **trim + BMP 이모지 허용** 제안. 개행만 금지. ❓ 세부 확인 필요 |
| **저장 필터 20개 채운 상태에서 신규 저장** | `SEARCH_003` 409 + 클라이언트가 "가장 오래된 필터 삭제 후 저장" 플로우 유도. 자동 삭제 금지. |
| **동시 요청 — 같은 이름으로 2건 동시 저장** | UNIQUE 부분 인덱스가 잡아냄 → 두 번째 요청 `SEARCH_002`. |
| **이름을 수정하려 했는데 기존 이름과 동일** | No-op. 200 반환 + `updated_at` 은 갱신 안 함 (추적 가치 없음). |
| **검색 키워드에 와일드카드/ILIKE 특수문자(`%`, `_`)** | 서버에서 `ESCAPE` 처리. 파라미터 바인딩으로 SQL Injection 방어. |
| **pg_trgm 미설치 환경에서 `similarity()` 사용** | `ILIKE` fallback. 운영 전 `CREATE EXTENSION pg_trgm` 마이그레이션 필요. |
| **저장 필터의 `filter_json` 크기 폭증** | 8KB 초과 시 400 (`SEARCH_001`) + 어떤 축이 큰지 메시지로 힌트. |
| **탈퇴 유예 중 저장 필터 사용** | 허용 (ACTIVE). 탈퇴 완료 시 hard delete ([03-common-policies.md §2.1](./03-common-policies.md)). |
| **`openNow=true` 필터 + 현재 KST 시각이 영업시간 `is_next_day=true` 구간** | Place 도메인에서 `is_next_day` 플래그로 처리 ([05-place-spec.md §9.3](./05-place-spec.md)). |

---

## 8. 성능 / 인덱싱 고려사항

### 8.1 검색 쿼리 성능

- **GIST 공간 인덱스** — `idx_places_location` 필수 (Place 도메인 §8.1).
- **카테고리/지역 필터** — `idx_places_status_category`, `idx_places_region`.
- **키워드 ILIKE** — MVP 허용. 데이터 10만건 이상 시 `pg_trgm` 도입 권장:
  ```sql
  CREATE EXTENSION IF NOT EXISTS pg_trgm;
  CREATE INDEX idx_places_name_trgm ON places USING GIN (name gin_trgm_ops);
  CREATE INDEX idx_places_address_trgm ON places USING GIN (address gin_trgm_ops);
  ```
- **복합 필터 EXISTS 서브쿼리** — `place_pet_policies (place_id, species)` PK 로 빠름. `place_amenities (place_id, amenity_code)` PK 로 빠름.
- **amenities 전체 매칭(모두 포함)** — 요청 태그 수가 많아질수록 비용 증가. 5개 이하 제한 권장 (§4.2 는 20 이지만 실무상 5 이내 UX).

### 8.2 캐시

| 캐시 | 키 | TTL | 무효화 |
|---|---|---|---|
| 저장 필터 목록 | `savedFilters::{memberId}` | 5분 | 생성/수정/삭제 시 key evict |
| 저장 필터 단건 | `savedFilter::{id}` | 10분 | 수정 시 evict |
| 검색 결과 자체 | **캐시 안 함** | - | 실시간성 + 파라미터 폭발 |

- 저장 필터는 회원당 최대 20개로 가벼움. 캐시 없이도 지연 미미.

### 8.3 쿼리 파라미터 직렬화

- 배열 파라미터는 **쉼표 구분** (`categories=RESTAURANT,CAFE`) 권장. 다중 파라미터(`categories=RESTAURANT&categories=CAFE`) 도 허용.
- 저장 필터 적용 시 서버가 `filter_json` → 쿼리 내부 표현으로 변환 → Place 검색 서비스로 위임 (동일 검색 경로 재사용).

### 8.4 확장: Elasticsearch 전환

- 데이터 100만건 이상 or p95 > 500ms 지속 시 Elasticsearch(OpenSearch) 도입. 본 문서의 `filter_json` 스키마는 ES DSL 로 1:1 번역 가능하도록 설계.

---

## 9. 의존성 & 향후 확장

### 9.1 의존 도메인

- **Place(D5)** — 검색 결과 원천 + 필터 매트릭스(카테고리/편의시설/동반정책/영업시간).
- **Member(D3)** — 저장 필터 소유자.
- **Pet(D4)** — 저장 필터 생성 시 대표 Pet 기반 프리셋(클라이언트 책임). 향후 "내 반려동물 기준 필터" 단축 버튼 도입 여지.

### 9.2 확장 포인트

| 확장 | Phase | 비고 |
|---|:---:|---|
| 검색 히스토리 + 자동완성 | Phase 4+ | `search_history` + Redis ZSet |
| 인기 검색어 실시간 피드 | Phase 4+ | Redis ZSet `search:trending` |
| 저장 필터 공유 (URL/딥링크) | Phase 5+ | `share_token` 또는 query compression |
| Elasticsearch 전환 | Phase 4+ | 한글 형태소 분석기(nori) |
| 자연어 검색 ("강남에서 소형견 가능한 식당") | Phase 5+ | LLM intent parsing |
| 저장 필터 알림 (새로 매칭되는 업체 알림) | Phase 5+ | Notification 도메인 연계 |
| 지도 뷰포트 기반 재검색 (`this area`) | Phase 3+ | `ST_MakeEnvelope` 기반 |
| 카테고리별 필터 프리셋 템플릿 | Phase 3+ | "로카유치원 전용", "카페 데이트" 등 관리자 큐레이션 |

### 9.3 ❓ 확인 필요 항목

1. **`amenities` 필터는 AND(모두 포함) vs OR(하나라도)** — UX 문서 기준 확정 필요. 현재 AND 제안.
2. **저장 필터 최대 개수 20** — 운영 데이터 후 조정.
3. **이름에 이모지/특수문자 허용 범위** — name 필드 공통 정책 ([03-common-policies.md](./03-common-policies.md))과 맞춤.
4. **저장 필터의 `filter.sort` 값을 적용 시에도 강제할지** — 현재는 overlay 우선. 저장 시점 정렬을 항상 따르도록 할지 UX 확정 필요.
5. **개별 축 바텀시트 7종의 정확한 역할** — Figma 의 `565:37178` 은 통합 시트로 확인됨. `565:37241~37560` 은 **각 축의 확장 편집 시트**로 추정 (카테고리/지역/거리/허용반려동물/편의시설/평점/영업중). Phase 4 UI/UX 문서에서 컴포넌트 관계 확정.
6. **빈 필터 저장 금지 정책** — 필요 시 "이름만 있는 빈 템플릿" 요구 사례 확인.
7. **`savedFilterId` + overlay 조합 규칙의 우선순위** — 현재 "overlay 우선". 키워드를 저장 필터에 넣고 overlay 로 지운 경우 등 엣지 케이스 UX 확정 필요.

---

## 10. 관련 문서

- [00. 서비스 개요](./00-overview.md)
- [01. 도메인 맵](./01-domain-map.md)
- [02. 용어집](./02-glossary.md)
- [03. 공통 정책](./03-common-policies.md)
- [05. 업체(Place) 기능 명세](./05-place-spec.md)
- [04. 반려동물(Pet) 기능 명세](./04-pet-spec.md) — 대표 Pet 기반 프리셋
