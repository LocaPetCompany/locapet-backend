# 07. 찜 & 최근 본 장소(Wishlist / Recently Viewed) 기능 명세

> 본 문서는 로카펫의 **D10. 찜/저장(Favorite)** 도메인 상세 기획이다.
> 상위 문서: [01. 도메인 맵](./01-domain-map.md), [02. 용어집](./02-glossary.md), [03. 공통 정책](./03-common-policies.md)

> **용어 주의**: 용어집(02-glossary)과 도메인맵(01-domain-map)에서는 도메인명을 **Favorite** 으로 통일.
> 본 명세서에서는 기능 관점에서 "찜(Wishlist)" 과 "최근 본 장소(Recently Viewed)" 2개 피처를 함께 다룬다.
> 테이블명은 가독성을 위해 `wishlists` 사용(찜 목록 의미). 엔티티 클래스명은 `Favorite` 유지 가능 — 구현 시점에 통일.

---

## 1. 개요

### 1.1 도메인 책임

- **찜(Wishlist)** — 회원이 관심 있는 업체를 북마크로 저장한다. 단순 토글(추가/해제). 1 회원 × 1 업체 = 1 찜.
- **최근 본 장소(Recently Viewed)** — 회원이 업체 상세 페이지를 조회한 이력을 서버에 저장한다. 여러 디바이스 간 동기화 목적. 최대 50개 유지, 오래된 것은 자동 퇴장(LRU).
- **집계 — 요즘 찜 많은 장소** — 최근 7일 신규 찜 증가량 상위 N개를 집계하여 홈/피드에 노출한다.

### 1.2 다른 도메인과의 관계

| 방향 | 대상 도메인 | 관계 |
|---|---|---|
| 참조 | Member(D3) | `member_id` → FK |
| 참조 | Place(D5) | `place_id` → FK |
| 피드백 | Place(D5) | `places.favorite_count` 캐시 갱신 |
| 피드백 | Feed(D6) | "요즘 찜 많은 장소" 섹션에 집계 결과 제공 |

---

## 2. 엔티티 & 데이터 모델

### 2.1 `wishlists` (찜)

| 컬럼 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `id` | BIGSERIAL | O | - | PK |
| `member_id` | BIGINT | O | - | FK → members.id |
| `place_id` | BIGINT | O | - | FK → places.id |
| `created_at` | TIMESTAMPTZ | O | NOW() | 찜한 시각 (집계 기준) |

- **UNIQUE** (`member_id`, `place_id`) — 중복 찜 방지.
- `updated_at` 없음 — 단순 교차 엔티티. 해제는 DELETE.
- 소프트 삭제 없음 — 해제 시 물리 삭제.

### 2.2 `recently_viewed_places` (최근 본 장소)

| 컬럼 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `id` | BIGSERIAL | O | - | PK |
| `member_id` | BIGINT | O | - | FK |
| `place_id` | BIGINT | O | - | FK |
| `viewed_at` | TIMESTAMPTZ | O | NOW() | 마지막 조회 시각 (UPSERT 시 갱신) |

- **UNIQUE** (`member_id`, `place_id`) — 업체당 1 row, 조회 시 `viewed_at` 갱신(upsert).
- INDEX: `idx_rvp_member_viewed (member_id, viewed_at DESC)` — 목록 조회.

### 2.3 집계 테이블/구조 (요즘 찜 많은)

**옵션 A — Redis ZSet (권장, MVP)**

```
KEY: wishlist:trending:7d
MEMBER: placeId
SCORE: 최근 7일 내 신규 찜 수 (배치로 갱신, 1시간마다)
```

- 조회: `ZREVRANGE wishlist:trending:7d 0 19 WITHSCORES` → 상위 20개 placeId.
- 장점: O(log N) 삽입/조회, TTL 관리 쉬움.
- 단점: Redis 의존 (이미 프로젝트에서 사용 중이므로 OK).

**옵션 B — DB 배치 테이블**

```sql
CREATE TABLE place_trending_daily (
  place_id BIGINT PRIMARY KEY,
  new_favorites_7d INT NOT NULL,
  computed_at TIMESTAMPTZ NOT NULL
);
```

- 배치(15분 ~ 1시간 주기)로 `places.favorite_count` 대신 신규 찜 7일 윈도우 카운트를 계산.

**결정**: MVP 는 **옵션 A (Redis ZSet)** 제안. DB 쿼리 부담 경감 + 읽기 빈도가 매우 높음.

### 2.4 제약 조건 & 인덱스

#### wishlists

| 종류 | 표현 | 목적 |
|---|---|---|
| UNIQUE | `(member_id, place_id)` | 중복 방지 |
| INDEX | `idx_wishlists_member_created (member_id, created_at DESC)` | 내 찜 목록 |
| INDEX | `idx_wishlists_place_created (place_id, created_at DESC)` | 집계 보조 (옵션 B 배치 계산 시) |

#### recently_viewed_places

| 종류 | 표현 | 목적 |
|---|---|---|
| UNIQUE | `(member_id, place_id)` | upsert 키 |
| INDEX | `idx_rvp_member_viewed` | 최신순 목록 |

### 2.5 상태 머신

- **wishlists**: 상태 없음. 존재 = 찜됨 / 없음 = 찜 해제.
- **recently_viewed_places**: 상태 없음. 리텐션 정책(최대 50개)은 애플리케이션에서 관리.

---

## 3. 주요 기능 (Use Case)

### 3.1 찜 추가

- **대상 화면**: 업체 상세 페이지의 하트 아이콘, 리스트 카드 하트 아이콘 (Place 상세 [480:50352](node-id=480:50352) 등).
- **엔드포인트**: `POST /api/v1/places/{placeId}/favorite`
- **사전조건**:
  - accessToken 필수.
  - 대상 Place 가 `PUBLISHED` (CLOSED 는 `PLACE_004` 409로 차단).
- **동작**:
  1. `INSERT INTO wishlists (member_id, place_id) VALUES (?, ?) ON CONFLICT DO NOTHING`.
  2. `UPDATE places SET favorite_count = favorite_count + 1 WHERE id = ?` (신규 INSERT 인 경우만).
  3. Redis ZSet `wishlist:trending:7d` 에 `ZINCRBY 1 {placeId}` — **실시간 반영 옵션** (선택).
- **출력**: `{ "placeId": 42, "isFavorited": true, "favoriteCount": 343 }`
- **Idempotent**: 이미 찜한 상태에서 POST 시 200 반환 (변동 없음).
- **에러**: `PLACE_001` (404), `PLACE_004` (409).

### 3.2 찜 해제

- **엔드포인트**: `DELETE /api/v1/places/{placeId}/favorite`
- **동작**:
  1. `DELETE FROM wishlists WHERE member_id = ? AND place_id = ?`.
  2. affected rows = 1 이면 `places.favorite_count = GREATEST(favorite_count - 1, 0)`.
  3. (선택) Redis ZSet 에 `ZINCRBY -1 {placeId}`.
- **Idempotent**: 존재하지 않으면 200 그대로.
- **에러**: `PLACE_001` (404) — 업체 자체가 없음.

### 3.3 내 찜 목록 조회

- **대상 화면**: [찜 목록](node-id=479:26968), [찜 Empty](node-id=479:26994)
- **엔드포인트**: `GET /api/v1/members/me/favorites?cursor=...&size=20&sort=latest|name`
- **응답 아이템** (Place 요약 + 찜한 시각):
  ```json
  {
    "placeId": 42,
    "name": "강아지와 식당",
    "category": "RESTAURANT",
    "address": "서울시 강남구 ...",
    "thumbnailImageUrl": "...",
    "ratingAvg": 4.6,
    "reviewCount": 128,
    "favoritedAt": "2026-04-16T08:20:00Z"
  }
  ```
- **Empty**: `items: []` + `hasNext: false`. 클라이언트는 Empty 화면 노출.
- **필터**:
  - ❓ 카테고리 필터 지원 여부 — MVP 는 미지원 제안(단순 최신순 목록).
- **권한**: accessToken 필수.

### 3.4 찜 상태 조회 (단건)

- **엔드포인트**: `GET /api/v1/places/{placeId}/favorite`
- **응답**: `{ "isFavorited": true, "favoriteCount": 343 }`
- **비고**: Place 상세 응답에 `isFavorited` 이미 포함되므로 별도 호출은 최소화. 목록 화면에서 다건 상태 조회가 필요하면 `/places` 응답에 이미 포함.

### 3.5 최근 본 장소 — 기록 (Upsert)

- **엔드포인트**: `POST /api/v1/members/me/recently-viewed`
- **입력**: `{ "placeId": 42 }`
- **동작**:
  ```sql
  INSERT INTO recently_viewed_places (member_id, place_id, viewed_at)
  VALUES (?, ?, NOW())
  ON CONFLICT (member_id, place_id)
  DO UPDATE SET viewed_at = EXCLUDED.viewed_at;
  ```
  그 후 **회원별 row 수가 50 초과 시 가장 오래된 것 삭제**:
  ```sql
  DELETE FROM recently_viewed_places
  WHERE id IN (
    SELECT id FROM recently_viewed_places
     WHERE member_id = ?
     ORDER BY viewed_at DESC
     OFFSET 50
  );
  ```
  - 구현 팁: 일반적으론 1건 초과만 발생하므로 `LIMIT 1` + OFFSET 50 으로 단건 제거 가능.
- **호출 시점**: 클라이언트가 업체 상세 진입 시 비동기로 호출. 중복 호출 방지는 클라이언트 책임 (같은 세션 내 동일 업체 재진입).
- **권한**: accessToken 필수.
- **응답**: 204 No Content 또는 간단 상태 반환.

### 3.6 최근 본 장소 — 목록 조회

- **대상 화면**:
  [최근 본 장소](node-id=479:26749), [선택 삭제 모드](node-id=479:26764), [Empty](node-id=559:12184)
- **엔드포인트**: `GET /api/v1/members/me/recently-viewed?cursor=...&size=20`
- **정렬**: `viewed_at DESC, id DESC`.
- **응답 아이템**: Place 요약 + `viewedAt`.
- **권한**: accessToken 필수.

### 3.7 최근 본 장소 — 삭제

- **단건 삭제**: `DELETE /api/v1/members/me/recently-viewed/{placeId}`
- **다건 일괄 삭제**: `POST /api/v1/members/me/recently-viewed/delete` `{ "placeIds": [1,2,3] }` (선택 삭제 모드 UX)
- **전체 삭제**: `DELETE /api/v1/members/me/recently-viewed` — 응답 204.
- **권한**: accessToken 필수. 타인 데이터 변조 불가.

### 3.8 요즘 찜 많은 장소 — 조회

- **엔드포인트**: `GET /api/v1/places/trending?window=7d&size=20`
- **동작**:
  1. Redis `ZREVRANGE wishlist:trending:7d 0 (size-1) WITHSCORES` 호출.
  2. placeId 집합에 대해 `SELECT ... FROM places WHERE id = ANY(?) AND status = 'PUBLISHED'` — 순서는 ZSet 순서 유지.
- **권한**: 비로그인 허용.
- **응답**: Place 요약 배열.
- **❓ 윈도우 옵션**: `window=7d` 고정 시작, 추후 `1d`/`30d` 지원 가능.

### 3.9 집계 갱신 배치

- **주기**: 1시간마다.
- **로직 (Redis ZSet 초기화 + 재계산)**:
  ```sql
  SELECT place_id, COUNT(*) AS cnt
    FROM wishlists
   WHERE created_at >= NOW() - INTERVAL '7 days'
   GROUP BY place_id
   ORDER BY cnt DESC
   LIMIT 500;
  ```
  결과를 `DEL wishlist:trending:7d` 후 `ZADD` 일괄 삽입.
- **장점**: 실시간 `ZINCRBY` 누락/드리프트 복구.
- **운영**: 실행 시간 < 1s 예상 (찜 일 1만건 기준).

---

## 4. 비즈니스 규칙 & 제약

### 4.1 도메인 불변식

1. **1 회원 × 1 업체 = 1 찜** (UNIQUE 물리 보장).
2. **찜은 토글** — 추가/해제만 존재. 상태 없음.
3. **CLOSED 업체는 신규 찜 불가.** 기존 찜은 유지 (해제는 가능).
4. **최근 본 장소는 회원당 최대 50개**. 초과 시 가장 오래된 것 자동 삭제.
5. **탈퇴 완료 시 wishlists / recently_viewed_places 는 hard delete** ([03-common-policies.md §2.1](./03-common-policies.md)).
6. **favorite_count 는 비정규화 캐시** — 애플리케이션이 변경 시 동반 갱신 + 야간 배치로 drift 보정.

### 4.2 입력 제약

| 필드 | 제약 |
|---|---|
| `placeIds` (일괄 삭제) | 1 ~ 50개 |
| `window` (trending) | `7d` (MVP 고정) |
| 최근 본 장소 최대 보유 | 50개 |

### 4.3 집계 정확도

- 실시간 ZSet 업데이트는 **근사값**. 정확값은 배치 재계산으로 보정.
- `favorite_count` 는 트랜잭션 내 증감 + 야간 배치 재계산:
  ```sql
  UPDATE places p SET favorite_count = (
    SELECT COUNT(*) FROM wishlists w WHERE w.place_id = p.id
  );
  ```

---

## 5. API 엔드포인트 초안 (REST)

### 5.1 찜 (Wishlist)

| Method | Path | Auth | 설명 |
|---|---|:---:|---|
| POST | `/api/v1/places/{placeId}/favorite` | accessToken | 찜 추가 (idempotent) |
| DELETE | `/api/v1/places/{placeId}/favorite` | accessToken | 찜 해제 (idempotent) |
| GET | `/api/v1/places/{placeId}/favorite` | accessToken | 찜 상태 단건 조회 |
| GET | `/api/v1/members/me/favorites` | accessToken | 내 찜 목록 |

### 5.2 최근 본 장소

| Method | Path | Auth | 설명 |
|---|---|:---:|---|
| POST | `/api/v1/members/me/recently-viewed` | accessToken | 조회 기록 (upsert) |
| GET | `/api/v1/members/me/recently-viewed` | accessToken | 목록 |
| DELETE | `/api/v1/members/me/recently-viewed/{placeId}` | accessToken | 단건 삭제 |
| POST | `/api/v1/members/me/recently-viewed/delete` | accessToken | 다건 일괄 삭제 |
| DELETE | `/api/v1/members/me/recently-viewed` | accessToken | 전체 삭제 |

### 5.3 집계 — 트렌딩

| Method | Path | Auth | 설명 |
|---|---|:---:|---|
| GET | `/api/v1/places/trending` | optional | 요즘 찜 많은 장소 |

### 5.4 응답 스키마 예시

#### 찜 추가/해제 응답

```json
{
  "placeId": 42,
  "isFavorited": true,
  "favoriteCount": 343
}
```

#### 내 찜 목록 응답

```json
{
  "items": [
    {
      "placeId": 42,
      "name": "강아지와 식당",
      "category": "RESTAURANT",
      "address": "서울시 강남구 ...",
      "thumbnailImageUrl": "...",
      "ratingAvg": 4.6,
      "reviewCount": 128,
      "favoriteCount": 343,
      "favoritedAt": "2026-04-16T08:20:00Z"
    }
  ],
  "nextCursor": "eyJpZCI6...",
  "hasNext": true
}
```

#### 최근 본 장소 응답

```json
{
  "items": [
    {
      "placeId": 42,
      "name": "강아지와 식당",
      "category": "RESTAURANT",
      "thumbnailImageUrl": "...",
      "ratingAvg": 4.6,
      "viewedAt": "2026-04-17T11:00:00Z"
    }
  ],
  "nextCursor": null,
  "hasNext": false
}
```

---

## 6. 에러 코드

| 코드 | HTTP | 메시지 | 발생 상황 |
|---|:---:|---|---|
| `FAVORITE_001` | 404 | 대상 업체를 찾을 수 없어요. | 없는 placeId (실제로는 `PLACE_001` 과 통합 가능) |
| `FAVORITE_002` | 409 | 폐업한 업체에는 찜할 수 없어요. | CLOSED Place (중복 정책과 일치: `PLACE_004` 재사용 가능) |
| `FAVORITE_003` | 400 | 요청 항목이 너무 많아요. | 다건 일괄 삭제 50개 초과 |
| `RVP_001` | 404 | 대상 업체를 찾을 수 없어요. | 없는 placeId |

> 네이밍 혼선 방지를 위해, `FAVORITE_001/002` 는 Place 측 에러(`PLACE_001/PLACE_004`)로 통일 반환해도 무방. 최종 결정은 Phase 5 API 명세 단계에서.

---

## 7. 엣지 케이스 & 예외 처리

| 케이스 | 처리 |
|---|---|
| **같은 업체에 동시에 두 번 찜 추가 요청** | UNIQUE 제약 + INSERT ... ON CONFLICT DO NOTHING. affected rows 기반으로 count 증감 여부 결정. |
| **찜 해제 직후 favorite_count 가 음수가 될 위험** | `GREATEST(favorite_count - 1, 0)` 방어. 또는 DB CHECK `favorite_count >= 0`. |
| **최근 본 장소 50 초과** | upsert 후 OFFSET 50 이후 row 삭제. 예상 성능 영향 미미. |
| **Place 가 `DELETED` / `HIDDEN` 로 전환** | 내 찜/최근 본 목록 조회 시 **제외 필터** 적용 (`status = 'PUBLISHED'` OR `status = 'CLOSED'` 만 노출). HIDDEN 리소스 노출 금지. |
| **Place 가 `CLOSED` 로 전환** | 내 찜 목록에는 노출 + "폐업" 뱃지. 신규 찜 추가는 불가. |
| **집계 ZSet 다운타임** | 트렌딩 API 는 Redis 실패 시 빈 배열 + 500 대신 200 응답 (graceful degradation). 경고 로그. |
| **찜 목록 cursor 무한 스크롤 중 중간에 업체가 HIDDEN 으로 전환** | 다음 페이지부터 제외. 이미 로드된 것은 클라이언트가 유지 (스킵 처리). |
| **탈퇴 유예 중 찜 조회** | 허용 (ACTIVE 상태). |
| **탈퇴 완료 시 데이터 정리** | wishlists / recently_viewed_places hard delete. `places.favorite_count` 는 배치 재계산으로 보정 (실시간 감산은 비용 대비 이득 적음). |

---

## 8. 성능 / 인덱싱 고려사항

### 8.1 주요 쿼리 패턴

| 패턴 | 빈도 | 인덱스 |
|---|:---:|---|
| 찜 추가/해제 | **매우 높음** | UNIQUE `(member_id, place_id)` |
| 내 찜 목록 (최신순) | 높음 | `idx_wishlists_member_created` |
| 특정 업체의 찜 여부 확인 | 매우 높음 (Place 상세) | UNIQUE (커버) |
| 최근 본 장소 upsert | 매우 높음 | UNIQUE (커버) |
| 최근 본 장소 목록 | 중간 | `idx_rvp_member_viewed` |
| 트렌딩 조회 | 매우 높음 (홈) | Redis ZSet `ZREVRANGE` |

### 8.2 캐시

| 캐시 | 키 | TTL | 비고 |
|---|---|---|---|
| `wishlist:trending:7d` (ZSet) | 단일 | 배치 재구성 (1h) | 메인 집계 |
| Place 상세의 `favorite_count` | Place 레벨 | 10분 (placeDetail 캐시와 동일) | 갱신은 Place 수정 흐름 |

- 내 찜 목록은 **캐시 대상 아님** (개인화 + 실시간성).

### 8.3 동시성

- `favorite_count` 증감: 원자 UPDATE. 매우 인기 업체도 초당 수십 건 수준이면 문제 없음.
- 매우 핫한 업체의 Place row 락 경합 가능 → 향후 **증감 버퍼링 + 배치 반영** 전략 (Phase 5+).

### 8.4 배치

- 야간 02:00 KST — `places.favorite_count` 재계산으로 drift 보정.
- 매시간 — `wishlist:trending:7d` 재구성.

---

## 9. 의존성 & 향후 확장

### 9.1 의존 도메인

- **Member, Place** — FK.
- **Feed(D6)** — 트렌딩 섹션 소비자.
- **Notification (Phase 3+)** — 찜한 업체 소식/이벤트 푸시.

### 9.2 확장 포인트

| 확장 | Phase | 비고 |
|---|:---:|---|
| 찜 폴더/태그 (여행/데일리/단골 등) | Phase 3+ | `wishlist_folders` 테이블 + 매핑 |
| 찜 공유 (URL/QR) | Phase 4+ | 폴더 단위 공개 링크 |
| 최근 검색어와 통합한 "최근 활동" 피드 | Phase 4+ | |
| 트렌딩 윈도우 다양화 (1d / 30d) | Phase 3+ | ZSet 키 네이밍 확장 |
| 지역별 트렌딩 | Phase 4+ | `wishlist:trending:7d:{regionCode}` |
| 찜 한 업체의 평점/리뷰 변화 알림 | Phase 4+ | Notification 도메인 연계 |

### 9.3 ❓ 확인 필요 항목

1. **`FAVORITE_001/002` 를 별도 코드로 둘지, `PLACE_001/004` 로 통합할지** — Phase 5 API 명세 단계에서 최종.
2. **찜 목록에 카테고리 필터 지원 여부** — MVP 미지원 제안.
3. **최근 본 장소 최대치 50 적정성** — 운영 후 조정.
4. **트렌딩 윈도우 `7d` 고정 적정성** — 초기 트래픽 데이터 부족 시 `14d` 도 고려.
5. **Redis 다운 시 트렌딩 fallback 전략** — 빈 배열 vs DB 실시간 쿼리.
6. **찜 수 증감 동시성 버퍼링 도입 시점** — Place 당 초당 수백 건 넘어가면 필요.
7. **탈퇴 완료 후 `favorite_count` 즉시 감산 여부** — 현재는 야간 배치 보정 제안.
