# 15. DB 스키마 & 마이그레이션 계획

> 본 문서는 Phase 1~4 기획서 14편을 바탕으로 **Phase 5 실제 구현용 DB 스키마와 Flyway 마이그레이션 계획**을 확정한다.
> 대상 DB: **PostgreSQL 16 + PostGIS**. ORM: **JPA/Hibernate**. 스키마 관리: **Flyway (`ddl-auto: validate`)**.
> 상위 문서: [14. API 스펙](./14-api-spec.md), [16. 구현 로드맵](./16-implementation-roadmap.md)

---

## 0. 현재 상태 (V001 ~ V004)

`domain/src/main/resources/db/migration/` 에 이미 적용된 마이그레이션:

| Version | 파일 | 핵심 내용 |
|---|---|---|
| V001 | `V001__create_meta_tables.sql` | `app_versions`, `maintenances`, `notices` (기본) |
| V002 | `V002__create_member_table.sql` | 초기 `members` (V004 에서 DROP 후 재생성) |
| V003 | `V003__convert_timestamp_columns_to_timestamptz.sql` | `TIMESTAMP` → `TIMESTAMPTZ` 전환 |
| V004 | `V004__restructure_members_add_social_and_identity.sql` | `members` 재구축 + `social_accounts` + `identity_locks` + `identity_verifications` |

**기존 테이블 (Phase 5 시작 시점)**:
- `app_versions`, `maintenances`, `notices` (기본 컬럼)
- `members` (2축 상태: `account_status` + `onboarding_stage`)
- `social_accounts`, `identity_locks`, `identity_verifications`

**글로벌 규칙** (이미 적용됨 — CLAUDE.md 참조):
- 모든 시각: `TIMESTAMPTZ`, JVM/Hibernate/JDBC/Docker 에서 UTC 통일
- 생년월일: `DATE` (타임존 불필요)
- Auto-increment: `BIGSERIAL` (JPA `GenerationType.IDENTITY` 호환)
- 인라인 `INDEX` 불가 — 별도 `CREATE INDEX` 문 필요
- `ENGINE=InnoDB`, `CHARSET=utf8mb4` 구문 사용 불가

---

## 1. Phase 5 마이그레이션 순서 전략

### 1.1 전체 로드맵

| Version | 내용 | 의존성 | 근거 |
|---|---|---|---|
| V005 | **PostGIS 확장 설치 + pg_trgm 확장** | 없음 | Place / 검색 인덱스 전 필수. 확장 설치는 독립된 마이그레이션에 둬서 롤백 영향 최소화 |
| V006 | **outbox_events** | 없음 | 도메인 이벤트(리뷰 좋아요, 공지 발행, 문의 답변)용. 모든 도메인 기반 |
| V006.5 | **terms + member_term_agreements** (M1.5 선행) | V004 | 약관 버전 관리 + 동의 이력. 2026-04-19 로드맵 조정으로 M8 → M1.5 앞당김. 온보딩 직후 법적 근거 확보 |
| V006.6 | **Member 약관 플래그 백필** (M1.5 보완) | V006.5 | 기존 `members.terms_of_service_agreed` 등 플래그 → `member_term_agreements` INSERT. Idempotent (`ON CONFLICT DO NOTHING`) |
| V007 | **places 기본 + place 인덱스** | V005(PostGIS) | Place 마스터 도메인 — 이후 Pet·Review·Wishlist 모두 이걸 참조 |
| V008 | **place 부속 (images / hours / amenities / pet_policies / category_meta)** | V007 | 서비스 상세 데이터 |
| V009 | **place_curations** (로카 추천) | V007 | 관리자 큐레이션. 별도 테이블로 분리 |
| V010 | **pets** | 없음 (members 는 V004) | Pet 도메인 — Review 에 앞서 구축 |
| V011 | **reviews + review_images + review_pets + review_likes + review_reports** | V007, V010 | Review 도메인. 5개 테이블을 하나의 V011로 묶어 트랜잭션 단위 확보 |
| V012 | **wishlists + recently_viewed_places** | V007 | 찜/최근 본. `places.favorite_count` 는 V007 에 포함 |
| V013 | **saved_filters** | V004(members) | 검색 필터 저장 |
| V014 | **notifications + device_tokens + notification_settings** | V004 | 알림 인앱 + 푸시 토큰 |
| V015 | **faqs** | 없음 | FAQ 도메인 |
| ~~V016~~ | ~~terms + member_term_agreements~~ → **V006.5 로 이동** (M1.5 선행) | — | 2026-04-19 로드맵 조정 |
| V017 | **notices 확장** (category/priority/thumbnail/status/start_at/end_at/published_at/send_push/pushed_at/deleted_at) | V001 | 기존 `notices` 에 ADD COLUMN |
| V018 | **inquiries + inquiry_images + inquiry_answers** | V004 | 1:1 문의 |
| V019 | **place 검색 성능 인덱스** (name_trgm / address_trgm 등) | V007, V005(pg_trgm) | 운영 초기엔 ILIKE 로 시작, 본 버전에서 인덱스 추가. 데이터 유입 후 적용 가능 |
| V020 | **admin_audit_logs** (M10) | 없음 | admin-api 쓰기 요청 감사 로그. M10 PR-38 에서 생성 |

**총 16개 신규 마이그레이션** (V005, V006, V006.5, V006.6, V007~V015, V017~V020). V016 은 V006.5 로 이동 완료.

### 1.2 순서 설계 원칙

1. **확장 설치(V005) 를 가장 먼저** — `postgis`, `pg_trgm` 는 이후 모든 마이그레이션에서 안전하게 참조 가능.
2. **outbox(V006) 도 기반 인프라** — 도메인 이벤트를 쓰는 모든 기능(리뷰 좋아요, 공지 발행, 문의 답변 푸시) 이전에 준비.
3. **Place(V007~V009) 를 먼저** — Pet 과 비교할 때 Place 쪽이 FK 피참조자가 더 많다. Place 스키마가 흔들리면 Review/Wishlist 모두 영향.
4. **Pet(V010) 은 Review(V011) 직전** — `review_pets.pet_id` FK 가 걸리기 때문. 스냅샷 구조라 Pet 이 없어도 Review 는 쓸 수 있지만 물리 FK 유지.
5. **notices 확장(V017) 은 후반** — 기존 운영 데이터에 영향 가기 때문에 다른 신규 테이블이 안정된 뒤 적용.
6. **검색 성능 인덱스(V019) 는 가장 마지막** — 실제 데이터가 들어온 뒤 의미가 있고, GIN 인덱스 생성은 비용이 큼.

---

## 2. 테이블 DDL (도메인별)

### 2.1 공통 인프라 (V005, V006)

#### V005: 확장 설치

```sql
-- domain/src/main/resources/db/migration/V005__install_extensions.sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 확인용 (선택) — 성공 시 아무것도 반환하지 않음
-- SELECT postgis_version();
```

> **운영 주의**: 로컬 `docker-compose.yml` 의 PostgreSQL 이미지는 `postgres:16-alpine` 이지만, PostGIS 확장이 포함된 이미지(`postgis/postgis:16-3.4-alpine` 등) 로 교체해야 한다. Testcontainers 에서도 동일 이미지 사용. 인프라 변경은 [16. 로드맵 M1](./16-implementation-roadmap.md) 의 PR-00 단계에서 진행.

#### V006: outbox_events

```sql
-- V006__create_outbox_events.sql
CREATE TABLE outbox_events (
    id           BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,          -- 'REVIEW', 'NOTICE', 'INQUIRY', 'WISHLIST' ...
    aggregate_id   BIGINT       NOT NULL,          -- 도메인 루트 PK
    event_type     VARCHAR(50)  NOT NULL,          -- 'REVIEW_LIKED', 'NOTICE_PUBLISHED' ...
    payload        JSONB        NOT NULL,          -- 이벤트 바디
    dedupe_key     VARCHAR(200),                   -- 24h 중복 차단 키 (예: 'REVIEW_LIKED:{reviewId}:{likerId}')
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING / PUBLISHED / FAILED
    attempt_count  INT          NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending_created
    ON outbox_events (created_at ASC)
    WHERE status = 'PENDING';

CREATE UNIQUE INDEX uk_outbox_dedupe_key
    ON outbox_events (dedupe_key)
    WHERE dedupe_key IS NOT NULL;
```

**설계 근거**:
- 트랜잭션 내에서 INSERT → `@TransactionalEventListener(AFTER_COMMIT)` 또는 별도 폴러가 PENDING 건을 FCM/APNs/알림 저장으로 처리.
- `dedupe_key` 로 `REVIEW_LIKED` 등 동일 이벤트 24h 내 중복 차단 ([09-notification-spec.md §7](./09-notification-spec.md)).
- 성공 후 `published_at` 설정 (hard delete 는 90일 주기 배치).

---

### 2.2 Place 도메인 (V007 ~ V009, V019)

#### V007: places + 기본 인덱스

```sql
-- V007__create_places.sql
CREATE TABLE places (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    category            VARCHAR(32)  NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    address             VARCHAR(255) NOT NULL,
    road_address        VARCHAR(255),
    detail_address      VARCHAR(100),
    region_code         VARCHAR(10),
    location            geography(Point, 4326) NOT NULL,
    phone               VARCHAR(32),
    website             VARCHAR(500),
    kakao_channel_id    VARCHAR(100),
    email               VARCHAR(255),
    description         TEXT,
    thumbnail_image_url VARCHAR(500),
    owner_member_id     BIGINT REFERENCES members(id),
    rating_avg          NUMERIC(2,1) NOT NULL DEFAULT 0.0,
    review_count        INT          NOT NULL DEFAULT 0,
    favorite_count      INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ,

    CONSTRAINT chk_places_category
        CHECK (category IN ('RESTAURANT','CAFE','KINDERGARTEN','LODGING','PARK')),
    CONSTRAINT chk_places_status
        CHECK (status IN ('DRAFT','PUBLISHED','HIDDEN','CLOSED')),
    CONSTRAINT chk_places_rating
        CHECK (rating_avg BETWEEN 0.0 AND 5.0),
    CONSTRAINT chk_places_review_count
        CHECK (review_count >= 0),
    CONSTRAINT chk_places_favorite_count
        CHECK (favorite_count >= 0)
);

-- 공간 인덱스 (반경 검색 핵심)
CREATE INDEX idx_places_location
    ON places USING GIST (location)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;

-- 공개 목록/카테고리 필터
CREATE INDEX idx_places_status_category
    ON places (category, status)
    WHERE deleted_at IS NULL;

-- 요즘 찜 많은 보조 정렬
CREATE INDEX idx_places_favorite_count
    ON places (favorite_count DESC)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;

-- 지역 필터
CREATE INDEX idx_places_region
    ON places (region_code)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
```

#### V008: place 부속 테이블

```sql
-- V008__create_place_sub_tables.sql

-- 이미지
CREATE TABLE place_images (
    id            BIGSERIAL PRIMARY KEY,
    place_id      BIGINT NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    image_url     VARCHAR(500) NOT NULL,
    display_order INT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT uk_place_images_place_order UNIQUE (place_id, display_order)
);
CREATE INDEX idx_place_images_place ON place_images (place_id) WHERE deleted_at IS NULL;

-- 운영 시간
CREATE TABLE place_hours (
    place_id     BIGINT NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    day_of_week  SMALLINT NOT NULL,           -- 0=Sun ~ 6=Sat (ISO)
    open_time    TIME,
    close_time   TIME,
    is_closed    BOOLEAN NOT NULL DEFAULT FALSE,
    is_next_day  BOOLEAN NOT NULL DEFAULT FALSE,  -- 자정 넘김 영업
    note         VARCHAR(100),
    PRIMARY KEY (place_id, day_of_week),
    CONSTRAINT chk_place_hours_dow CHECK (day_of_week BETWEEN 0 AND 6)
);

-- 편의시설
CREATE TABLE place_amenities (
    place_id      BIGINT NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    amenity_code  VARCHAR(30) NOT NULL,
    PRIMARY KEY (place_id, amenity_code)
);

-- 허용 반려동물 정책 (species 당 1 row)
CREATE TABLE place_pet_policies (
    place_id        BIGINT NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    species         VARCHAR(10) NOT NULL,     -- DOG / CAT
    max_size        VARCHAR(10) NOT NULL,     -- SMALL / MEDIUM / LARGE
    requires_cage   BOOLEAN NOT NULL DEFAULT FALSE,
    indoor_allowed  BOOLEAN NOT NULL DEFAULT TRUE,
    extra_fee_krw   INT,
    notes           VARCHAR(300),
    PRIMARY KEY (place_id, species),
    CONSTRAINT chk_ppp_species  CHECK (species IN ('DOG','CAT')),
    CONSTRAINT chk_ppp_max_size CHECK (max_size IN ('SMALL','MEDIUM','LARGE'))
);

-- 카테고리별 메타 (JSONB)
CREATE TABLE place_category_meta (
    place_id BIGINT PRIMARY KEY REFERENCES places(id) ON DELETE CASCADE,
    meta     JSONB NOT NULL DEFAULT '{}'::jsonb
);
```

#### V009: place_curations (로카 추천)

```sql
-- V009__create_place_curations.sql
CREATE TABLE place_curations (
    id             BIGSERIAL PRIMARY KEY,
    place_id       BIGINT NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    display_order  INT NOT NULL,           -- 작을수록 상단
    title          VARCHAR(100),           -- 선택: 큐레이션 타이틀 ("이번 주 로카 추천")
    description    VARCHAR(300),           -- 선택: 한 줄 설명
    visible_from   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    visible_until  TIMESTAMPTZ,            -- NULL = 무기한
    created_by     BIGINT REFERENCES members(id),  -- 관리자 id
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at     TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_place_curations_place_active
    ON place_curations (place_id)
    WHERE deleted_at IS NULL AND (visible_until IS NULL OR visible_until > NOW());

CREATE INDEX idx_place_curations_display
    ON place_curations (display_order ASC)
    WHERE deleted_at IS NULL
      AND visible_from <= NOW()
      AND (visible_until IS NULL OR visible_until > NOW());
```

#### V019: 검색 성능 인덱스 (pg_trgm)

```sql
-- V019__add_place_search_indexes.sql
CREATE INDEX idx_places_name_trgm
    ON places USING GIN (name gin_trgm_ops)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;

CREATE INDEX idx_places_address_trgm
    ON places USING GIN (address gin_trgm_ops)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
```

---

### 2.3 Pet 도메인 (V010)

```sql
-- V010__create_pets.sql
CREATE TABLE pets (
    id                      BIGSERIAL PRIMARY KEY,
    member_id               BIGINT NOT NULL REFERENCES members(id),
    name                    VARCHAR(20) NOT NULL,
    species                 VARCHAR(10) NOT NULL,
    breed                   VARCHAR(50),
    gender                  VARCHAR(10) NOT NULL,
    birth_date              DATE,
    is_birth_date_estimated BOOLEAN NOT NULL DEFAULT FALSE,
    weight_kg               NUMERIC(4,1),
    size                    VARCHAR(10) NOT NULL,
    is_neutered             BOOLEAN,
    profile_image_url       VARCHAR(500),
    personality_tags        VARCHAR(255),   -- CSV: "활발,친화적"
    is_primary              BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at              TIMESTAMPTZ,

    CONSTRAINT chk_pets_species CHECK (species IN ('DOG','CAT')),
    CONSTRAINT chk_pets_gender  CHECK (gender  IN ('MALE','FEMALE','UNKNOWN')),
    CONSTRAINT chk_pets_size    CHECK (size    IN ('SMALL','MEDIUM','LARGE')),
    CONSTRAINT chk_pets_name_len CHECK (char_length(name) BETWEEN 1 AND 20),
    CONSTRAINT chk_pets_weight  CHECK (weight_kg IS NULL OR (weight_kg > 0 AND weight_kg <= 200))
);

-- 회원당 활성 대표 1마리 보장
CREATE UNIQUE INDEX uk_pets_member_primary_active
    ON pets (member_id)
    WHERE is_primary = TRUE AND deleted_at IS NULL;

-- 회원별 활성 목록
CREATE INDEX idx_pets_member_active
    ON pets (member_id)
    WHERE deleted_at IS NULL;

-- 맞춤리뷰 집계 보조
CREATE INDEX idx_pets_species_size
    ON pets (species, size)
    WHERE deleted_at IS NULL;
```

---

### 2.4 Review 도메인 (V011)

```sql
-- V011__create_reviews.sql

-- 본체
CREATE TABLE reviews (
    id             BIGSERIAL PRIMARY KEY,
    place_id       BIGINT NOT NULL REFERENCES places(id),
    member_id      BIGINT NOT NULL REFERENCES members(id),
    rating         SMALLINT NOT NULL,
    content        TEXT NOT NULL,
    visit_date     DATE,
    has_pet        BOOLEAN NOT NULL DEFAULT TRUE,   -- Phase 5 확정: false 면 review_pets 없음
    like_count     INT NOT NULL DEFAULT 0,
    report_count   INT NOT NULL DEFAULT 0,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    hidden_reason  VARCHAR(50),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at     TIMESTAMPTZ,

    CONSTRAINT chk_reviews_rating  CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_status  CHECK (status IN ('ACTIVE','HIDDEN','DELETED')),
    CONSTRAINT chk_reviews_content CHECK (char_length(content) BETWEEN 10 AND 2000),
    CONSTRAINT chk_reviews_likes   CHECK (like_count >= 0),
    CONSTRAINT chk_reviews_reports CHECK (report_count >= 0)
);

-- 1 회원 × 1 업체 = 1 리뷰 (소프트 삭제된 것까지 포함해서 전역 UNIQUE — 재작성 금지 정책)
CREATE UNIQUE INDEX uk_reviews_place_member
    ON reviews (place_id, member_id);

-- 업체별 최신순/평점순/도움순
CREATE INDEX idx_reviews_place_status_created
    ON reviews (place_id, status, created_at DESC, id DESC);
CREATE INDEX idx_reviews_place_status_rating
    ON reviews (place_id, status, rating DESC, id DESC);
CREATE INDEX idx_reviews_place_status_like
    ON reviews (place_id, status, like_count DESC, id DESC);

-- 내 리뷰
CREATE INDEX idx_reviews_member_created
    ON reviews (member_id, created_at DESC)
    WHERE deleted_at IS NULL;


-- 리뷰 이미지
CREATE TABLE review_images (
    id            BIGSERIAL PRIMARY KEY,
    review_id     BIGINT NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    image_url     VARCHAR(500) NOT NULL,
    display_order INT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT uk_review_images_order UNIQUE (review_id, display_order)
);


-- 리뷰 ↔ 반려동물 (N:M + 스냅샷)
CREATE TABLE review_pets (
    review_id         BIGINT NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    pet_id            BIGINT NOT NULL REFERENCES pets(id),
    pet_name_snapshot VARCHAR(20)  NOT NULL,
    species_snapshot  VARCHAR(10)  NOT NULL,
    size_snapshot     VARCHAR(10)  NOT NULL,
    breed_snapshot    VARCHAR(50),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (review_id, pet_id),
    CONSTRAINT chk_rp_species CHECK (species_snapshot IN ('DOG','CAT')),
    CONSTRAINT chk_rp_size    CHECK (size_snapshot    IN ('SMALL','MEDIUM','LARGE'))
);

-- 맞춤리뷰 매칭 인덱스
CREATE INDEX idx_review_pets_species_size
    ON review_pets (species_snapshot, size_snapshot);
CREATE INDEX idx_review_pets_pet ON review_pets (pet_id);


-- 좋아요 (본인금지, idempotent)
CREATE TABLE review_likes (
    review_id  BIGINT NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    member_id  BIGINT NOT NULL REFERENCES members(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (review_id, member_id)
);


-- 리뷰 신고 (5건 이상 자동 HIDDEN)
CREATE TABLE review_reports (
    id                   BIGSERIAL PRIMARY KEY,
    review_id            BIGINT NOT NULL REFERENCES reviews(id) ON DELETE CASCADE,
    reporter_member_id   BIGINT NOT NULL REFERENCES members(id),
    reason_code          VARCHAR(30) NOT NULL,
    memo                 VARCHAR(500),
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at          TIMESTAMPTZ,
    CONSTRAINT chk_rr_reason CHECK (reason_code IN ('SPAM','ABUSE','FALSE_INFO','PRIVACY','ETC')),
    CONSTRAINT chk_rr_status CHECK (status IN ('PENDING','REVIEWED','REJECTED')),
    CONSTRAINT uk_review_reports_unique UNIQUE (review_id, reporter_member_id, reason_code)
);

CREATE INDEX idx_review_reports_review ON review_reports (review_id);
CREATE INDEX idx_review_reports_status ON review_reports (status, created_at);
```

**Phase 5 확정 변경사항**:
- `has_pet BOOLEAN NOT NULL DEFAULT TRUE` 추가 — 리뷰 작성 시 "반려동물 없음" 체크 허용.
- `has_pet=true` 일 때 `review_pets` 에 최소 1행 필수. `has_pet=false` 일 때는 `review_pets` 행 없음.
  - 서비스 레이어에서 검증 (DB CHECK 로는 두 테이블 교차 제약 표현이 어려움).
- 자동 HIDDEN 임계값: **5건** (기획서 3건 → Phase 5 확정 5건). 서비스 코드에서 하드코딩.

---

### 2.5 Wishlist / Recently Viewed (V012)

```sql
-- V012__create_wishlists_and_rvp.sql

CREATE TABLE wishlists (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT NOT NULL REFERENCES members(id),
    place_id   BIGINT NOT NULL REFERENCES places(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_wishlists_member_place UNIQUE (member_id, place_id)
);

CREATE INDEX idx_wishlists_member_created ON wishlists (member_id, created_at DESC);
CREATE INDEX idx_wishlists_place_created  ON wishlists (place_id,  created_at DESC);


CREATE TABLE recently_viewed_places (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT NOT NULL REFERENCES members(id),
    place_id   BIGINT NOT NULL REFERENCES places(id),
    viewed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_rvp_member_place UNIQUE (member_id, place_id)
);

CREATE INDEX idx_rvp_member_viewed
    ON recently_viewed_places (member_id, viewed_at DESC);
```

**집계 테이블은 Redis** 에 둠 (`wishlist:trending:7d` ZSet). DB 스키마 불필요.

---

### 2.6 Saved Filter (V013)

```sql
-- V013__create_saved_filters.sql
CREATE TABLE saved_filters (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT NOT NULL REFERENCES members(id),
    name            VARCHAR(30) NOT NULL,
    filter_json     JSONB NOT NULL,
    last_applied_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,

    CONSTRAINT chk_saved_filters_name CHECK (char_length(name) BETWEEN 1 AND 30)
);

-- 회원당 활성 이름 중복 방지
CREATE UNIQUE INDEX uk_saved_filters_member_name_active
    ON saved_filters (member_id, name)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_saved_filters_member_active
    ON saved_filters (member_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_saved_filters_member_last_applied
    ON saved_filters (member_id, last_applied_at DESC NULLS LAST)
    WHERE deleted_at IS NULL;
```

**`filter_json` 스키마** (unknown 필드 무시, Phase 5 확정):
- `categories: string[]` (최대 5)
- `petFilter: { species: string[], sizes: string[], weightKgMin, weightKgMax }`
- `amenities: string[]` (최대 20) — **OR 연산** (Phase 5 확정 변경)
- `restrictions: { indoorAllowed, outdoorAllowed, dangerousDogAllowed, cageFree }`
- `minRating: number`, `openNow: boolean`
- `location: { lat, lng, radiusMeters }`
- `keyword: string`
- `sort: 'distance' | 'latest' | 'rating' | 'popular'`

---

### 2.7 Notification (V014)

```sql
-- V014__create_notifications.sql

-- 인앱 알림
CREATE TABLE notifications (
    id           BIGSERIAL PRIMARY KEY,
    member_id    BIGINT NOT NULL REFERENCES members(id),
    type         VARCHAR(30) NOT NULL,
    title        VARCHAR(100) NOT NULL,
    body         VARCHAR(300) NOT NULL,
    payload_json JSONB,
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ,
    CONSTRAINT chk_noti_type CHECK (type IN ('REVIEW_LIKED','ANNOUNCEMENT','SYSTEM','INQUIRY_ANSWERED'))
);

CREATE INDEX idx_notifications_member_created
    ON notifications (member_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_notifications_member_unread
    ON notifications (member_id)
    WHERE is_read = FALSE AND deleted_at IS NULL;


-- 디바이스 토큰
CREATE TABLE device_tokens (
    id              BIGSERIAL PRIMARY KEY,
    member_id       BIGINT NOT NULL REFERENCES members(id),
    platform        VARCHAR(10) NOT NULL,
    token           VARCHAR(500) NOT NULL,
    app_version     VARCHAR(20),
    locale          VARCHAR(10),
    last_active_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_dt_platform CHECK (platform IN ('IOS','ANDROID')),
    CONSTRAINT uk_device_tokens_token UNIQUE (token)
);

CREATE INDEX idx_device_tokens_member_active
    ON device_tokens (member_id)
    WHERE is_active = TRUE;


-- 알림 설정 (1:1)
CREATE TABLE notification_settings (
    member_id           BIGINT PRIMARY KEY REFERENCES members(id),
    push_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    types_json          JSONB NOT NULL DEFAULT '{}'::jsonb,
    quiet_hours_start   TIME,
    quiet_hours_end     TIME,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

### 2.8 FAQ (V015)

```sql
-- V015__create_faqs.sql
CREATE TABLE faqs (
    id            BIGSERIAL PRIMARY KEY,
    category      VARCHAR(30) NOT NULL,
    question      VARCHAR(200) NOT NULL,
    answer        TEXT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    status        VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ,

    CONSTRAINT chk_faqs_category CHECK (category IN (
        'ACCOUNT','PET','PLACE','REVIEW','FAVORITE',
        'NOTIFICATION','PAYMENT','ETC'
    )),
    CONSTRAINT chk_faqs_status CHECK (status IN ('DRAFT','PUBLISHED')),
    CONSTRAINT chk_faqs_q_len  CHECK (char_length(question) BETWEEN 1 AND 200),
    CONSTRAINT chk_faqs_order  CHECK (display_order BETWEEN 0 AND 9999)
);

CREATE INDEX idx_faqs_category_order
    ON faqs (category, display_order, id)
    WHERE status = 'PUBLISHED' AND deleted_at IS NULL;
```

---

### 2.9 Terms (V006.5 — M1.5 선행)

> 2026-04-19 조정: 원 V016 → **V006.5** 로 번호 이동. M1.5 시점에 실행되어 온보딩 직후 법적 근거 확보.
> 백필 마이그레이션 `V006.6__backfill_member_term_agreements.sql` 가 직후에 실행됨.

```sql
-- V6_5__create_terms.sql  (Flyway 파일명. 점은 언더스코어로)

CREATE TABLE terms (
    id             BIGSERIAL PRIMARY KEY,
    code           VARCHAR(30) NOT NULL,
    version        VARCHAR(20) NOT NULL,
    title          VARCHAR(200) NOT NULL,
    content        TEXT NOT NULL,
    is_required    BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    effective_to   TIMESTAMPTZ,
    status         VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_terms_code   CHECK (code IN (
        'SERVICE_TERMS','PRIVACY_POLICY','LOCATION_TERMS',
        'MARKETING_CONSENT','PUSH_CONSENT'
    )),
    CONSTRAINT chk_terms_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    CONSTRAINT uk_terms_code_version UNIQUE (code, version)
);

-- 약관 종류당 현재 유효 버전은 1개
CREATE UNIQUE INDEX uk_terms_code_current
    ON terms (code)
    WHERE status = 'PUBLISHED' AND effective_to IS NULL;


CREATE TABLE member_term_agreements (
    id            BIGSERIAL PRIMARY KEY,
    member_id     BIGINT NOT NULL REFERENCES members(id),
    term_id       BIGINT NOT NULL REFERENCES terms(id),
    agreed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ip_address    VARCHAR(45),
    user_agent    VARCHAR(300),
    withdrawn_at  TIMESTAMPTZ,
    CONSTRAINT uk_term_agreements_member_term UNIQUE (member_id, term_id)
);

CREATE INDEX idx_term_agreements_member ON member_term_agreements (member_id);
CREATE INDEX idx_term_agreements_term   ON member_term_agreements (term_id);
```

---

### 2.10 notices 확장 (V017)

기존 V001 에서 생성된 `notices` 테이블에 컬럼 추가.

```sql
-- V017__extend_notices.sql
ALTER TABLE notices
    ADD COLUMN IF NOT EXISTS category            VARCHAR(30) NOT NULL DEFAULT 'GENERAL',
    ADD COLUMN IF NOT EXISTS priority            VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS thumbnail_image_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS status              VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN IF NOT EXISTS start_at            TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS end_at              TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS published_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS send_push           BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS pushed_at           TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS deleted_at          TIMESTAMPTZ;

ALTER TABLE notices
    ADD CONSTRAINT chk_notices_category
        CHECK (category IN ('GENERAL','UPDATE','EVENT','URGENT'));
ALTER TABLE notices
    ADD CONSTRAINT chk_notices_priority
        CHECK (priority IN ('NORMAL','PINNED'));
ALTER TABLE notices
    ADD CONSTRAINT chk_notices_status
        CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED'));

-- 기존 row 는 즉시 PUBLISHED 로 노출되도록 published_at 백필
UPDATE notices SET published_at = created_at WHERE published_at IS NULL;

-- 목록 조회 인덱스
CREATE INDEX idx_notices_list
    ON notices (status, priority DESC, published_at DESC)
    WHERE deleted_at IS NULL;
```

> **카테고리는 기획서의 4종** 그대로 (`GENERAL/UPDATE/EVENT/URGENT`). "plain text + `\n`" 본문 포맷 유지.

---

### 2.11 Inquiry (V018)

```sql
-- V018__create_inquiries.sql

CREATE TABLE inquiries (
    id           BIGSERIAL PRIMARY KEY,
    member_id    BIGINT NOT NULL REFERENCES members(id),
    category     VARCHAR(30) NOT NULL,
    title        VARCHAR(50) NOT NULL,
    content      VARCHAR(1000) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    answered_at  TIMESTAMPTZ,
    closed_at    TIMESTAMPTZ,
    deleted_at   TIMESTAMPTZ,

    CONSTRAINT chk_inquiries_category CHECK (category IN (
        'SERVICE_ERROR','PLACE_INFO_ERROR','ACCOUNT_ISSUE',
        'REVIEW_REPORT_REQUEST','SUGGESTION','ETC'
    )),
    CONSTRAINT chk_inquiries_status CHECK (status IN ('PENDING','ANSWERED','CLOSED')),
    CONSTRAINT chk_inquiries_title_len   CHECK (char_length(title)   BETWEEN 1 AND 50),
    CONSTRAINT chk_inquiries_content_len CHECK (char_length(content) BETWEEN 1 AND 1000)
);

CREATE INDEX idx_inquiries_member_created
    ON inquiries (member_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_inquiries_status_created
    ON inquiries (status, created_at DESC)
    WHERE deleted_at IS NULL;


CREATE TABLE inquiry_images (
    id            BIGSERIAL PRIMARY KEY,
    inquiry_id    BIGINT NOT NULL REFERENCES inquiries(id) ON DELETE CASCADE,
    image_url     VARCHAR(500) NOT NULL,
    display_order INT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT uk_inquiry_images_order UNIQUE (inquiry_id, display_order),
    CONSTRAINT chk_inquiry_images_order CHECK (display_order BETWEEN 0 AND 4)
);


CREATE TABLE inquiry_answers (
    id          BIGSERIAL PRIMARY KEY,
    inquiry_id  BIGINT NOT NULL REFERENCES inquiries(id) ON DELETE CASCADE,
    admin_id    BIGINT NOT NULL REFERENCES members(id),
    content     TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_inquiry_answers_inquiry UNIQUE (inquiry_id),
    CONSTRAINT chk_inquiry_answers_len    CHECK (char_length(content) BETWEEN 1 AND 5000)
);

CREATE INDEX idx_inquiry_answers_admin ON inquiry_answers (admin_id, created_at DESC);
```

**Phase 5 확정 변경사항**:
- 카테고리 Enum 에서 `FEATURE_REQUEST` → **`SUGGESTION`** 로 변경 (6종 유지).

---

## 3. 인덱스 전략 요약

### 3.1 공통 패턴

- **부분 인덱스 (Partial Index) 적극 활용** — `WHERE deleted_at IS NULL`, `WHERE status = 'PUBLISHED'` 등. PostgreSQL 의 강점.
- **외래키 컬럼 수동 인덱스 필수** — PostgreSQL 은 FK 에 자동 인덱스를 만들지 않음.
- **복합 인덱스 컬럼 순서**: 선택도 높은 컬럼 → 정렬 컬럼 순. 예: `(place_id, status, created_at DESC)`.
- **커버링 인덱스 (Covering Index, INCLUDE 절)** — PostgreSQL 11+ 지원. 필요 시 Phase 후반에 튜닝.

### 3.2 GIST / GIN

| 인덱스 | 대상 | 용도 |
|---|---|---|
| GIST | `places.location` | 반경 검색 (`ST_DWithin`), 거리 정렬 (`ST_Distance`) |
| GIN (pg_trgm) | `places.name`, `places.address` | 부분 일치 검색 (`ILIKE '%키워드%'`) |

### 3.3 UNIQUE 부분 인덱스 — 비즈니스 규칙 물리 보장

- `pets`: `WHERE is_primary = TRUE AND deleted_at IS NULL` — 회원당 활성 대표 1마리
- `reviews`: `(place_id, member_id)` 전역 UNIQUE — 1 회원 × 1 업체 = 1 리뷰 (재작성 금지)
- `wishlists`: `(member_id, place_id)` UNIQUE — 중복 찜 방지
- `recently_viewed_places`: `(member_id, place_id)` UNIQUE — upsert 키
- `saved_filters`: `WHERE deleted_at IS NULL` — 회원당 활성 이름 UNIQUE
- `device_tokens`: `(token)` UNIQUE — 토큰 유일성
- `terms`: `WHERE status = 'PUBLISHED' AND effective_to IS NULL` — 약관 종류당 현재 1개
- `member_term_agreements`: `(member_id, term_id)` UNIQUE
- `review_reports`: `(review_id, reporter_member_id, reason_code)` UNIQUE — 중복 신고 차단
- `inquiry_answers`: `(inquiry_id)` UNIQUE — 1 문의 = 1 답변
- `place_curations`: `WHERE deleted_at IS NULL AND (visible_until IS NULL OR visible_until > NOW())` — 한 업체의 활성 큐레이션은 1개

---

## 4. 성능 고려 사항

### 4.1 비정규화 집계 컬럼

| 컬럼 | 갱신 트리거 | 보정 방법 |
|---|---|---|
| `places.favorite_count` | `wishlists` INSERT/DELETE 동일 트랜잭션 | 야간 02:00 KST 배치 재계산 |
| `places.review_count` | `reviews` INSERT/DELETE/HIDDEN 동일 트랜잭션 | 야간 배치 |
| `places.rating_avg` | `reviews` 변동 시 `AVG()` 재계산 (방식 a) | 야간 배치 |
| `reviews.like_count` | `review_likes` INSERT/DELETE 동일 트랜잭션 | 야간 배치 (선택) |
| `reviews.report_count` | `review_reports` INSERT | 야간 배치 (선택) |

**배치 구현**: Spring `@Scheduled(cron = "0 0 17 * * *")` (= 02:00 KST / 17:00 UTC)  
위치: `app-api/src/main/kotlin/com/vivire/locapet/app/application/scheduler/FavoriteCountRecalcJob.kt` 등.

### 4.2 트리거 vs 애플리케이션 카운터 — 선택 기준

- **애플리케이션 카운터 (선택)** — MVP 에서는 서비스 레이어 `@Transactional` 내 `UPDATE places SET favorite_count = favorite_count + 1` 이 단순하고 충분.
- **DB 트리거 (미선택)** — 분산된 경로에서 쓸 때만. JPA 와 함께 쓰면 flush 순서 문제 발생 가능.

### 4.3 recently_viewed_places — 50개 초과 정리

옵션 A (단일 트랜잭션 내 정리):
```sql
INSERT INTO recently_viewed_places (member_id, place_id, viewed_at)
VALUES (?, ?, NOW())
ON CONFLICT (member_id, place_id)
DO UPDATE SET viewed_at = EXCLUDED.viewed_at;

DELETE FROM recently_viewed_places
 WHERE id IN (
     SELECT id FROM recently_viewed_places
      WHERE member_id = ?
      ORDER BY viewed_at DESC
      OFFSET 50
 );
```

옵션 B (배치 청소): 매시간 `OFFSET 50` 이후 모든 row hard delete.  
**MVP 는 A** (upsert 시점 정리). 성능 부담 미미.

### 4.4 notifications 90일 정리

매일 03:00 KST 배치:
```sql
DELETE FROM notifications
 WHERE created_at < NOW() - INTERVAL '90 days';

DELETE FROM device_tokens
 WHERE is_active = FALSE
   AND last_active_at < NOW() - INTERVAL '30 days';
```

---

## 5. V005 ~ V019 마이그레이션 파일 — 인덱스

최종 Flyway 파일 **16개** (2026-04-19 조정: V016 → V006.5 이동 + V006.6 백필 신규 + V020 admin audit 추가).

| 파일명 | 포함 오브젝트 | 실행 시점 | 참조 섹션 |
|---|---|---|---|
| `V005__install_extensions.sql` | postgis, pg_trgm | M1 | §2.1 |
| `V006__create_outbox_events.sql` | outbox_events | M1 | §2.1 |
| `V6_5__create_terms.sql` | terms, member_term_agreements | **M1.5** | §2.9 |
| `V6_6__backfill_member_term_agreements.sql` | 기존 Member 약관 플래그 백필 | **M1.5** | §2.9 |
| `V007__create_places.sql` | places + 인덱스 | M2 | §2.2 |
| `V008__create_place_sub_tables.sql` | place_images, place_hours, place_amenities, place_pet_policies, place_category_meta | M2 | §2.2 |
| `V009__create_place_curations.sql` | place_curations | M2 | §2.2 |
| `V010__create_pets.sql` | pets + 인덱스 | M3 | §2.3 |
| `V011__create_reviews.sql` | reviews, review_images, review_pets, review_likes, review_reports | M4 | §2.4 |
| `V012__create_wishlists_and_rvp.sql` | wishlists, recently_viewed_places | M5 | §2.5 |
| `V013__create_saved_filters.sql` | saved_filters | M6 | §2.6 |
| `V014__create_notifications.sql` | notifications, device_tokens, notification_settings | M7 | §2.7 |
| `V015__create_faqs.sql` | faqs | M8 | §2.8 |
| `V017__extend_notices.sql` | notices ADD COLUMN + 인덱스 | M8 | §2.10 |
| `V018__create_inquiries.sql` | inquiries, inquiry_images, inquiry_answers | M9 | §2.11 |
| `V019__add_place_search_indexes.sql` | name_trgm, address_trgm | M6/M11 | §2.2 |
| `V020__create_admin_audit_logs.sql` | admin_audit_logs | M10 | PR-38 |

> V016 은 비어있음 (V006.5 로 이동 완료). 이후 신규 마이그레이션은 V021+ 부터 채번.

---

## 6. Hibernate Spatial 연동 지침

### 6.1 엔티티에서 `geography(Point,4326)` 매핑

```kotlin
// domain/src/main/kotlin/com/vivire/locapet/domain/place/Place.kt
import org.locationtech.jts.geom.Point

@Entity
@Table(name = "places")
class Place(
    // ...
    @Column(name = "location", columnDefinition = "geography(Point,4326)", nullable = false)
    var location: Point,
    // ...
)
```

**의존성**:
```kotlin
// domain/build.gradle.kts
dependencies {
    implementation("org.hibernate.orm:hibernate-spatial")
    implementation("org.locationtech.jts:jts-core:1.19.0")
}
```

**Dialect**: Spring Boot 가 `PostgreSQLDialect` 를 자동 감지. `hibernate-spatial` 만 classpath 에 있으면 geography 타입이 `Point` 로 매핑됨.

### 6.2 Point 생성 유틸

```kotlin
// common/src/main/kotlin/com/vivire/locapet/common/util/GeoUtil.kt
object GeoUtil {
    private val factory = GeometryFactory(PrecisionModel(), 4326)
    fun point(lng: Double, lat: Double): Point = factory.createPoint(Coordinate(lng, lat))
}
```

**주의**: WGS84 에서 순서는 `(lng, lat)`. JSON 응답/요청에서는 `{ lat, lng }` 로 노출하고 내부에서만 뒤집는다.

### 6.3 반경 검색 쿼리 예시

QueryDSL 또는 native query:
```kotlin
@Query(value = """
    SELECT p.*, ST_Distance(p.location, ST_MakePoint(:lng, :lat)::geography) AS distance_m
      FROM places p
     WHERE p.status = 'PUBLISHED' AND p.deleted_at IS NULL
       AND ST_DWithin(p.location, ST_MakePoint(:lng, :lat)::geography, :radius)
     ORDER BY distance_m ASC, p.id DESC
     LIMIT :size
""", nativeQuery = true)
fun findNearby(lat: Double, lng: Double, radius: Int, size: Int): List<PlaceNearbyRow>
```

---

## 7. 약관 재동의 대기 상태 — 쓰기 차단 구현 (아키텍처 결정)

### 7.1 문제

`terms` 에 신규 버전을 발행하면 기존 회원은 **재동의 필요 상태**가 된다. 이 상태에서는:
- 읽기 API (조회): **허용**
- 쓰기 API (리뷰 작성, 찜, 문의 등): **차단** → 클라이언트가 재동의 플로우로 이동

### 7.2 구현 방식 비교

| 방식 | 장점 | 단점 | 판정 |
|---|---|---|---|
| **A. Spring Security Filter** | 인증 직후 검사, SecurityContext 확장 용이 | 경로 매칭 복잡 (쓰기/읽기 구분) | 후보 |
| **B. HandlerInterceptor** (`@Component implements HandlerInterceptor`) | Controller 메서드의 HTTP method 쉽게 접근 | Security 와 이중 체인 | **채택** |
| **C. AOP (`@Around`)** | 서비스 메서드 단위 정밀 제어 | 읽기/쓰기 구분을 어노테이션으로 태깅해야 함 | 보류 |

**결정 — B 인터셉터 방식**:
- `app-api/src/main/kotlin/com/vivire/locapet/app/global/security/TermsAgreementInterceptor.kt`
- `WebMvcConfigurer.addInterceptors` 에 등록하되 **경로 화이트리스트** + **HTTP method=GET 제외** 로 쓰기만 차단.
- 인증이 안 된 요청은 패스 (Security Filter 가 먼저 처리).
- 인증된 회원 중 `pendingRequiredTerms > 0` 이면 `TERMS_001` (400) 또는 전용 코드 `TERMS_006` (409 Conflict: "재동의 필요") 반환.

**구현 스켈레톤**:
```kotlin
@Component
class TermsAgreementInterceptor(
    private val termsService: TermsService
) : HandlerInterceptor {
    override fun preHandle(req: HttpServletRequest, res: HttpServletResponse, handler: Any): Boolean {
        if (req.method == "GET" || req.method == "OPTIONS") return true
        val memberId = SecurityContextHolder.getContext().authentication?.principal as? Long
            ?: return true  // 비인증 경로는 패스
        if (isWhitelisted(req.requestURI)) return true
        if (termsService.hasPendingRequiredAgreements(memberId)) {
            throw TermsAgreementRequiredException()  // → 409 TERMS_006
        }
        return true
    }
    private fun isWhitelisted(uri: String) = uri in setOf(
        "/api/v1/members/me/term-agreements",     // 재동의 제출 자체는 허용
        "/api/v1/auth/logout",
        "/api/v1/members/me/withdraw"
    )
}
```

**캐싱**: `hasPendingRequiredAgreements` 는 로그인마다 호출되므로 **Redis `terms:pending:{memberId}` TTL 1시간** 캐시. 관리자가 약관 발행 시 `terms:pending:*` 전체 evict.

---

## 8. 탈퇴 시 데이터 처리 (공통 정책 §2.1 확장)

30일 유예 (`WITHDRAW_REQUESTED`) → `WITHDRAWN` 전환 시 처리:

| 테이블 | 처리 |
|---|---|
| `pets` | `DELETE` (hard) — 맞춤리뷰는 스냅샷이라 무관 |
| `wishlists` | `DELETE` |
| `recently_viewed_places` | `DELETE` |
| `saved_filters` | `DELETE` |
| `reviews` | `status=HIDDEN` + `member_id` 유지 (닉네임은 응답에서 "탈퇴한 회원" 렌더) |
| `review_likes`, `review_reports` | 유지 |
| `notifications`, `device_tokens`, `notification_settings` | `DELETE` |
| `inquiries` | 유지 (감사 로그) — 조회 시 작성자 마스킹 |
| `member_term_agreements` | 유지 (법적 근거) |
| `social_accounts` | `DELETE` |
| `members` | `status=WITHDRAWN`, 개인정보 마스킹 (`nickname`, `email`, `phone`, `name`, `profile_image_url`) |
| `identity_verifications`, `identity_locks` | 유지 (재가입 Cooldown, 감사) |

**구현**: `app-api/src/main/kotlin/com/vivire/locapet/app/application/scheduler/WithdrawalFinalizeJob.kt`  
매일 03:00 KST `withdrawal_effective_at < NOW()` 인 회원을 순회하며 트랜잭션 처리.

---

## 9. ERD (텍스트 스케치)

```
members ─┬─< pets
         ├─< social_accounts
         ├─< reviews  >─ places ─┬─< place_images
         │       │                ├─< place_hours
         │       ├─< review_pets >─ pets
         │       ├─< review_images          (스냅샷 저장)
         │       ├─< review_likes
         │       └─< review_reports
         │
         ├─< wishlists >─ places
         ├─< recently_viewed_places >─ places
         ├─< saved_filters
         ├─< notifications
         ├─< device_tokens
         ├─1 notification_settings
         ├─< member_term_agreements >─ terms
         ├─< inquiries ─1 inquiry_answers
         │       └─< inquiry_images
         └─< identity_verifications

places ──< place_amenities
      ├──< place_pet_policies
      ├──1 place_category_meta (JSONB)
      └──< place_curations

notices                   (독립)
app_versions              (독립)
maintenances              (독립)
faqs                      (독립)
outbox_events             (독립, 모든 이벤트 공통)
identity_locks            (CI hash 단독 키)
```

---

## 10. 로컬 개발 체크리스트

Phase 5 시작 전 확인:

- [ ] `docker-compose.yml` 의 PostgreSQL 이미지를 **PostGIS 포함 이미지**로 교체 (`postgis/postgis:16-3.4-alpine`).
- [ ] Testcontainers 도 동일 이미지로 변경 (`IntegrationTestSupport.kt`).
- [ ] `domain/build.gradle.kts` 에 `hibernate-spatial`, `jts-core` 추가.
- [ ] 로컬 DB 초기화 후 V001~V019 순차 적용 확인.
- [ ] `SELECT postgis_version()`, `SELECT extname FROM pg_extension WHERE extname IN ('postgis','pg_trgm')` 로 확장 검증.

---

## 11. 관련 문서

- [03. 공통 정책](./03-common-policies.md) — 탈퇴 데이터 처리, 에러 코드 컨벤션
- [04. 반려동물(Pet)](./04-pet-spec.md)
- [05. 업체(Place)](./05-place-spec.md)
- [06. 리뷰(Review)](./06-review-spec.md)
- [07. 찜(Wishlist)](./07-wishlist-spec.md)
- [08. 검색/필터(Search)](./08-search-filter-spec.md)
- [09. 알림(Notification)](./09-notification-spec.md)
- [10. 공지/FAQ/약관](./10-announcement-spec.md)
- [11. 1:1 문의(Inquiry)](./11-inquiry-spec.md)
- [14. API 스펙](./14-api-spec.md)
- [16. 구현 로드맵](./16-implementation-roadmap.md)
