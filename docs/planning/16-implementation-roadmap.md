# 16. 구현 로드맵 (Implementation Roadmap)

> 본 문서는 Phase 5 실제 구현을 **마일스톤 → PR 단위**로 분해한 실행 워크북이다.
> 각 PR 에는 작업 체크리스트, 실제 코드 파일 경로, 마이그레이션 참조를 포함한다.
> 상위 문서: [14. API 스펙](./14-api-spec.md), [15. DB 스키마](./15-db-schema.md)

---

## 0. 목표 / 전제

### 0.1 범위

- **MVP 스코프** — [00-overview.md §5.1](./00-overview.md) 의 Must Have 영역 전체.
- **테스트 가능한 증분 PR** — 각 PR 종료 시 서버가 기동하고 신규 엔드포인트가 curl/통합 테스트로 검증 가능.
- **모듈러 모놀리스 유지** — `app-api`, `admin-api`, `domain`, `common` 4 모듈 경계 존중.

### 0.2 기술 가정

- Kotlin 2.2.21 / Spring Boot 4.0.1 / JDK 21 / PostgreSQL 16 + PostGIS
- JPA `ddl-auto: validate`, Flyway 로 스키마 관리
- JWT + Spring Security (`app-api`), 별도 ID/PW 로그인 (`admin-api`)
- Redis (캐시 + 이벤트 + ZSet 트렌딩)
- S3 + CloudFront + Presigned URL (이미지)
- FCM + APNs (푸시)

### 0.3 Phase 5 확정 6개 변경사항 반영

| # | 변경 | 적용 PR |
|---|---|---|
| 1 | 편의시설 필터 **OR** 연산 | PR-21 (Search) |
| 2 | 리뷰 신고 5건 자동 HIDDEN | PR-17 (Review Report) |
| 3 | 문의 카테고리 SUGGESTION | PR-37 (Inquiry) |
| 4 | `/api/v1/meta/splash` 통합 엔드포인트 | PR-32 (Meta) |
| 5 | 업체 신고 `POST /api/v1/places/{id}/report` | PR-15 (Place Report) |
| 6 | 리뷰 `hasPet=false` 허용 | PR-16 (Review 작성) |

---

## 1. 마일스톤 개요

| Milestone | 기간 | 핵심 산출물 | 주요 의존성 |
|---|---|---|---|
| **M1 기반** | 1~2주 | 공통 인프라, 확장 설치, outbox, 업로드 | 없음 |
| **M1.5 Terms 기반** | 1주 | `terms` + `member_term_agreements` + 백필 + 재동의 쓰기 차단 인터셉터 | M1 |
| **M2 Place** | 2~3주 | Place CRUD (admin) + 조회 (app) + 검색 | M1 |
| **M3 Pet** | 1~2주 | Pet CRUD + 대표 지정 | M1 |
| **M4 Review** | 2~3주 | Review CRUD + 좋아요 + 신고 + 맞춤리뷰 | M2, M3 |
| **M5 Wishlist** | 1주 | 찜 + 최근 본 + 트렌딩 ZSet | M2 |
| **M6 Search** | 2주 | Saved Filter + 통합 검색 매트릭스 | M2 |
| **M7 Notification** | 2주 | 인앱 + FCM/APNs + 설정 + 방해금지 | M1(outbox), M4(이벤트 소스) |
| **M8 Meta 확장** | 1주 | 공지 확장 + FAQ + 스플래시 통합 | M1, M1.5 |
| **M9 Inquiry** | 1주 | 1:1 문의 + 답변 + 알림 연동 | M7 |
| **M10 Admin** | 1~2주 | admin-api 로그인 + CMS + seed 스크립트 | M2~M9 |
| **M11 운영 안정화** | 1주 | 배치, 캐시 무효화, 모니터링, 부하테스트 | 전체 |

**총 예상 기간**: 15~20주 (1~1.5 인 백엔드, 테스트/QA 포함). M1.5 삽입으로 Terms 기반이 온보딩 직후 확보됨 → 법적 리스크 선제 완화.

> **2026-04-19 조정 사항** (Phase 4 로드맵 검토 결과):
> 1. Terms 관련 PR (원 PR-31, PR-33) → **M1.5** (PR-04a, PR-04b) 로 이동. Terms 마이그레이션도 **V016 → V006.5** 로 번호 조정
> 2. 약관 플래그 백필 마이그레이션 **신규 V006.6** 명시
> 3. admin 초기 seed 계정 스크립트 → **M10 PR-38** 체크리스트 추가
> 4. 이벤트 Dedupe Key 컨벤션 → **§13.5** 신설

---

## 2. M1 — 기반 레이어

### PR-00: 로컬/테스트 인프라 업그레이드

**목표**: PostGIS 포함 이미지로 전환, Testcontainers 호환.

- [ ] `docker-compose.yml` PostgreSQL 이미지 → `postgis/postgis:16-3.4-alpine`
- [ ] `app-api/src/test/kotlin/com/vivire/locapet/app/support/IntergrationTestSupport.kt` (오타 유지) 의 PostgreSQL 컨테이너도 동일 이미지로 변경
- [ ] 기존 V001~V004 마이그레이션이 신규 이미지에서도 정상 적용되는지 확인
- [ ] `domain/build.gradle.kts` 에 추가:
  ```kotlin
  implementation("org.hibernate.orm:hibernate-spatial")
  implementation("org.locationtech.jts:jts-core:1.19.0")
  ```
- [ ] `./gradlew clean build` + 기존 Auth/Onboarding 테스트 green 확인

### PR-01: 확장 + outbox_events

- [ ] `domain/src/main/resources/db/migration/V005__install_extensions.sql` — postgis, pg_trgm
- [ ] `domain/src/main/resources/db/migration/V006__create_outbox_events.sql`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/outbox/OutboxEvent.kt` — Entity
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/outbox/OutboxEventRepository.kt`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/outbox/OutboxEventStatus.kt` — Enum
- [ ] 서비스 레이어에서 `OutboxEvent` 를 저장하는 일반 유틸 (`common/src/main/kotlin/com/vivire/locapet/common/outbox/OutboxWriter.kt`)

**동작 검증**:
```bash
docker-compose up -d
./gradlew :app-api:bootRun    # 마이그레이션 자동 적용
psql -h localhost -U root -d locapet -c "SELECT extname FROM pg_extension;"
# postgis, pg_trgm, plpgsql 존재 확인
```

### PR-02: 공통 Cursor 페이지네이션 + 에러 응답 확장

- [ ] `common/src/main/kotlin/com/vivire/locapet/common/page/CursorPageRequest.kt`
- [ ] `common/src/main/kotlin/com/vivire/locapet/common/page/CursorPage.kt`
  ```kotlin
  data class CursorPage<T>(val items: List<T>, val nextCursor: String?, val hasNext: Boolean)
  ```
- [ ] `common/src/main/kotlin/com/vivire/locapet/common/page/CursorCodec.kt` — Base64(JSON) 인코딩/디코딩
- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/global/exception/ErrorResponse.kt` → `details: Map<String, Any>? = null` 필드 추가 (기존 호환 유지)
- [ ] `GlobalExceptionHandler` 의 `MethodArgumentNotValidException` 핸들러를 `details.fieldErrors` 포함하도록 확장

### PR-03: S3 클라이언트 + Presigned URL

- [ ] `common/src/main/kotlin/com/vivire/locapet/common/config/AwsS3ConfigProperties.kt` — bucket, region, cdnBaseUrl
- [ ] `common/src/main/kotlin/com/vivire/locapet/common/config/AwsS3Config.kt` — `S3Presigner` Bean
- [ ] `common/build.gradle.kts` 에 `software.amazon.awssdk:s3:2.25.xx` 추가
- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/upload/service/UploadService.kt`
- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/upload/controller/UploadController.kt`
- [ ] DTO: `PresignedUrlRequest`, `PresignedUrlResponse`
- [ ] `UploadPurpose` Enum: `PET_PROFILE`, `REVIEW`, `INQUIRY`, `MEMBER_PROFILE`, `NOTICE_THUMBNAIL`
- [ ] `application.yml` 에 `app.aws.s3.*` 설정
- [ ] 이미지 메타 저장 (신규 테이블 `uploaded_media` 는 V020+ 고려, MVP 는 Redis 키만으로 충분)

**단위 통합 테스트**:
```bash
curl -X POST http://localhost:8080/api/v1/uploads/presigned-url \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{ "purpose": "REVIEW", "contentType": "image/jpeg", "fileSize": 1048576 }'
```

### PR-04: Outbox Publisher + TransactionalEventListener

- [ ] `common/src/main/kotlin/com/vivire/locapet/common/outbox/OutboxPublisher.kt` — `@Scheduled(fixedDelay=5000)` 로 PENDING 폴링 or `@TransactionalEventListener(AFTER_COMMIT)` 즉시
- [ ] `common/src/main/kotlin/com/vivire/locapet/common/outbox/DomainEvent.kt` 마커 인터페이스
- [ ] 더미 이벤트 테스트: Auth 로그인 성공 시 Outbox 에 `MEMBER_LOGGED_IN` 저장 → Publisher 가 로그만 찍음 (실제 소비자는 M7 에서 연결)

---

## 2.5. M1.5 — Terms & 재동의 기반

> **배경**: 온보딩의 약관 동의 근거를 버전 관리된 `terms` 테이블로 정식화하기 위해 Phase 4 검토에서 M8 → M1.5 로 앞당김.
> 기존 `Member` 의 `termsOfServiceAgreed` / `privacyPolicyAgreed` / `marketingConsent` 플래그는 하위 호환으로 유지하되, 진실 원본은 `member_term_agreements` 로 이관.

### PR-04a: Terms 엔티티 + 마이그레이션 + Member 플래그 백필

- [ ] `V006.5__create_terms.sql` — `terms`, `member_term_agreements`
  - 신규 Enum `TermCode`: `SERVICE_TERMS`, `PRIVACY_POLICY`, `LOCATION_TERMS`, `MARKETING_CONSENT`, `AGE_14_PLUS`
  - 현재 유효 버전 UNIQUE 부분 인덱스 (`effective_to IS NULL WHERE status='PUBLISHED'`)
  - Flyway 파일명은 `V6_5__create_terms.sql` (점은 언더스코어로)
- [ ] `V006.6__backfill_member_term_agreements.sql` (Q4 빠진 항목 보완)
  - 기존 `members.terms_of_service_agreed=true` 레코드 → `member_term_agreements (member_id, term_id, agreed_at)` INSERT
  - 조건: 해당 시점에 유효한 `terms.id` 를 서브쿼리로 조회 (선행 INSERT 로 `SERVICE_TERMS v1` 등 시드 필요)
  - `privacy_policy_agreed`, `marketing_consent` 동일 처리
  - Idempotent: `ON CONFLICT DO NOTHING`
  - Flyway 파일명: `V6_6__backfill_member_term_agreements.sql`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/meta/Term.kt`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/meta/MemberTermAgreement.kt`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/meta/TermCode.kt`, `TermStatus.kt`
- [ ] `TermRepository`, `MemberTermAgreementRepository`
- [ ] `app-api` 공개 조회:
  - `GET /api/v1/meta/terms`
  - `GET /api/v1/meta/terms/{code}`
- [ ] `app-api` 회원 동의 기록:
  - `POST /api/v1/members/me/term-agreements` (이용 중 동의 추가)
  - `GET /api/v1/members/me/term-agreements`
- [ ] 온보딩 `profile/complete` 플로우에 `termAgreements` 배열 통합 — 기존 boolean 플래그는 동시에 `true` 로 유지 (하위 호환)

### PR-04b: 재동의 쓰기 차단 인터셉터

- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/global/security/TermsAgreementInterceptor.kt` (HandlerInterceptor 방식, §[15-db-schema.md §7](./15-db-schema.md))
- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/global/config/WebMvcConfig.kt` — 등록
- [ ] 화이트리스트:
  - `GET` / `OPTIONS` 전체 통과
  - `POST /api/v1/members/me/term-agreements` (재동의 자체는 가능)
  - `POST /api/v1/auth/logout`, `POST /api/v1/members/me/withdraw`
  - `/api/v1/meta/**` (공지/약관/앱버전 등 공개 조회)
- [ ] 판정 로직: 로그인 회원이 보유한 `member_term_agreements` 대비 `terms` 의 현재 PUBLISHED 중 `is_required=TRUE` 버전 미동의 시 차단
- [ ] Redis `terms:pending:{memberId}` TTL 1h 캐시 (평상시 판정 비용 최소화)
- [ ] 관리자 약관 발행 시 (M8 이후 관리자 CMS 연결): `terms:pending:*` 일괄 evict
- [ ] 에러 코드: `TERMS_006` (409 CONFLICT — `PENDING_RE_AGREEMENT`)
- [ ] 통합 테스트:
  - `app-api/src/test/kotlin/com/vivire/locapet/app/global/security/TermsAgreementInterceptorIntegrationTest.kt`
  - 미동의 회원이 `POST /api/v1/reviews` 요청 → 409, 재동의 후 정상
  - 화이트리스트 경로 정상 통과

**M1.5 완료 체크**:
- [ ] 온보딩 신규 회원: `members.terms_of_service_agreed=true` + `member_term_agreements` row 동시 생성
- [ ] 기존 회원(백필): `member_term_agreements` row 존재
- [ ] 약관 신규 발행 시 기존 사용자 `TERMS_006` 으로 차단됨 (수동 테스트: admin CRUD 는 M10 이전까지 직접 INSERT 로 검증)

---

## 3. M2 — Place 도메인

### PR-05: Place 엔티티 + 마이그레이션

- [ ] `V007__create_places.sql`
- [ ] `V008__create_place_sub_tables.sql`
- [ ] `V009__create_place_curations.sql`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/place/Place.kt` (geography(Point,4326) 매핑)
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/place/PlaceCategory.kt`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/place/PlaceStatus.kt`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/place/PlaceImage.kt`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/place/PlaceHours.kt` (`@IdClass` 또는 `@EmbeddedId`)
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/place/PlaceAmenity.kt`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/place/PlacePetPolicy.kt`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/place/PlaceCategoryMeta.kt` (JSONB — `hibernate-types` 또는 `@JdbcTypeCode(SqlTypes.JSON)`)
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/place/PlaceCuration.kt`
- [ ] 각 Repository
- [ ] `common/src/main/kotlin/com/vivire/locapet/common/util/GeoUtil.kt` — `point(lng,lat)` 팩토리

### PR-06: Place 관리자 CRUD (admin-api)

- [ ] `admin-api/src/main/kotlin/com/vivire/locapet/admin/api/place/controller/AdminPlaceController.kt`
- [ ] `admin-api/src/main/kotlin/com/vivire/locapet/admin/api/place/service/AdminPlaceService.kt`
- [ ] DTO: `PlaceCreateRequest`, `PlaceUpdateRequest`, `PlaceAdminResponse`
- [ ] 상태 전환 엔드포인트 4종 (publish/hide/close/delete)
- [ ] 부속 PUT 엔드포인트 (hours/amenities/pet-policies/category-meta)

> **admin-api 인증**은 M10 에서 정식 추가. 이 단계에서는 임시로 `@Profile("dev")` + Basic Auth 스텁 사용 가능.

### PR-07: Place 상세 조회 (app-api)

- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/place/controller/PlaceController.kt`
- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/place/service/PlaceQueryService.kt`
- [ ] DTO: `PlaceDetailResponse`
- [ ] 비로그인 전화 마스킹 구현 (`phone` → `02-****-5678`, 지역별 규칙)
- [ ] `SecurityConfig` 에 `/api/v1/places/**` GET 공개 추가
- [ ] Redis 캐시: `@Cacheable("placeDetail")`

### PR-08: Place 반경 검색 + 검색 API

- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/place/service/PlaceSearchService.kt`
- [ ] QueryDSL 또는 native query로 `ST_DWithin` 사용
- [ ] CursorPage 적용
- [ ] `/api/v1/places/search`, `/api/v1/places/near` 구현
- [ ] `openNow` KST 고정 해석 유틸: `common/src/main/kotlin/com/vivire/locapet/common/time/KstClockUtil.kt`

### PR-09: Place Curation (로카 추천) + Trending 조회 스텁

- [ ] `/api/v1/places/recommended` — `place_curations` 조회
- [ ] `/api/v1/places/trending` — Redis `wishlist:trending:7d` 빈 배열 fallback (M5 에서 실제 채움)
- [ ] admin-api `/admin/api/v1/place-curations` CRUD

---

## 4. M3 — Pet 도메인

### PR-10: Pet 엔티티 + 마이그레이션

- [ ] `V010__create_pets.sql`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/pet/Pet.kt`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/pet/Species.kt`, `PetGender.kt`, `PetSize.kt`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/pet/PetRepository.kt` — `findAllActiveByMemberId`, `findPrimaryByMemberId`, `countActiveByMemberId`

### PR-11: Pet CRUD + 대표 지정

- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/pet/controller/PetController.kt`
- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/pet/service/PetService.kt`
- [ ] DTO: `PetCreateRequest`, `PetUpdateRequest`, `PetResponse`, `PetListResponse`
- [ ] 대표 지정 로직: 트랜잭션 내 기존 대표 FALSE → 신규 TRUE
- [ ] 대표 Pet 삭제 시 자동 승격 (`created_at ASC`)
- [ ] Species 변경 차단 (`PET_003`)
- [ ] 10마리 초과 차단 (`PET_004`)

### PR-12: 대표 Pet 캐시

- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/pet/service/PrimaryPetCacheService.kt`
- [ ] Redis key: `pet:primary:{memberId}` TTL 5분
- [ ] 등록/수정/삭제/대표변경 시 evict
- [ ] Review 도메인 맞춤리뷰 필터링에 사용 (M4)

---

## 5. M4 — Review 도메인

### PR-13: Review 엔티티 + 마이그레이션

- [ ] `V011__create_reviews.sql` (5개 테이블 포함)
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/review/Review.kt` — `hasPet` boolean 포함
- [ ] `ReviewImage.kt`, `ReviewPet.kt`, `ReviewLike.kt`, `ReviewReport.kt`
- [ ] `ReviewStatus.kt`, `ReviewReportReason.kt`, `ReviewSort.kt`

### PR-14: Review 작성/수정/삭제 (hasPet 분기 포함)

- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/review/controller/ReviewController.kt`
- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/review/service/ReviewCommandService.kt`
- [ ] DTO: `ReviewCreateRequest`, `ReviewUpdateRequest`, `ReviewResponse`
- [ ] **hasPet 분기 검증**:
  - `hasPet=true` → `petIds` 1~5개 필수, 본인 소유 + 활성
  - `hasPet=false` → `petIds` 금지 (있으면 400)
- [ ] Pet 속성 스냅샷 복제 로직
- [ ] `reviews.uk_reviews_place_member` 충돌 시 `REVIEW_002` (409)
- [ ] 트랜잭션 내 `places.review_count` / `rating_avg` 갱신

### PR-15: Place 신고 API (Phase 5 확정 신규)

- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/place/controller/PlaceReportController.kt`
- [ ] `POST /api/v1/places/{id}/report` — 내부적으로 `inquiries (category=PLACE_INFO_ERROR)` 또는 별도 `place_reports` 테이블.
- [ ] **MVP 결정**: `inquiries` 재사용 + `related_place_id` 필드 추가 (V020 마이그레이션).
- [ ] 중복 신고 차단 (24h 내 동일 member × place): Redis `place:report:dedupe:{placeId}:{memberId}` key TTL 24h

### PR-16: 리뷰 조회 + 정렬 4종 + 맞춤리뷰

- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/review/service/ReviewQueryService.kt`
- [ ] `GET /api/v1/places/{placeId}/reviews?sort=latest|rating_high|rating_low|helpful|tailored`
- [ ] 정렬별 인덱스 활용 (V011 인덱스)
- [ ] **tailored 로직**: 대표 Pet 조회 → `review_pets.species_snapshot = primary.species AND size_snapshot = primary.size` 조인. **hasPet=false 리뷰는 tailored 에서 제외**.
- [ ] 대표 Pet 없으면 `REVIEW_006` (400)
- [ ] `GET /api/v1/reviews/{id}`, `GET /api/v1/members/me/reviews`

### PR-17: Review 좋아요 + 신고 + 자동 HIDDEN (Phase 5 확정)

- [ ] `POST /api/v1/reviews/{id}/like`, `DELETE` (본인 리뷰 금지 `REVIEW_008`)
- [ ] `POST /api/v1/reviews/{id}/report`
- [ ] **신고 5건 이상 자동 HIDDEN** (Phase 5 확정):
  ```kotlin
  if (reportCountAfterIncrement >= 5) {
      review.status = HIDDEN
      review.hiddenReason = "REPORTED_THRESHOLD"
  }
  ```
- [ ] Outbox 이벤트 발행: `REVIEW_LIKED` (자기자신 필터링 포함)
- [ ] 중복 신고 차단 (`review_reports` UNIQUE)

---

## 6. M5 — Wishlist / Recently Viewed

### PR-18: Wishlist 엔티티 + CRUD

- [ ] `V012__create_wishlists_and_rvp.sql`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/wishlist/Wishlist.kt`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/wishlist/RecentlyViewedPlace.kt`
- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/wishlist/controller/WishlistController.kt`
- [ ] `POST|DELETE /api/v1/members/me/favorites/{placeId}` — 트랜잭션 내 `places.favorite_count ±= 1`
- [ ] `GET /api/v1/members/me/favorites` (Cursor)
- [ ] CLOSED 업체 신규 찜 차단 (`PLACE_004`)
- [ ] 내 찜 목록은 HIDDEN 필터링

### PR-19: Recently Viewed + 50개 LRU

- [ ] `POST /api/v1/members/me/recently-viewed` — upsert + 50 초과 시 가장 오래된 것 삭제
- [ ] `GET`, `DELETE /{placeId}`, `DELETE` (전체), `POST /delete` (다건)

### PR-20: Trending ZSet + 배치

- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/application/scheduler/TrendingRecomputeJob.kt` — `@Scheduled(cron = "0 */15 * * * *")`
- [ ] Redis ZSet `wishlist:trending:7d` 재계산 (최근 7일 `wishlists` GROUP BY)
- [ ] `/api/v1/places/trending` 엔드포인트 실제 동작 (PR-09 의 스텁 교체)
- [ ] Redis 다운 시 빈 배열 200 반환 (graceful degradation)
- [ ] 야간 02:00 KST `favorite_count` 재계산 배치: `app-api/src/main/kotlin/com/vivire/locapet/app/application/scheduler/FavoriteCountRecalcJob.kt`

---

## 7. M6 — Search / Saved Filter

### PR-21: 통합 검색 매트릭스 (편의시설 OR — Phase 5 확정)

- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/place/service/PlaceSearchCriteria.kt` — 검색 DTO
- [ ] 검색 쿼리 빌더: QueryDSL 또는 dynamic native SQL
- [ ] **`amenities` 필터 OR 매칭**:
  ```sql
  AND EXISTS (
    SELECT 1 FROM place_amenities pa
    WHERE pa.place_id = p.id
      AND pa.amenity_code = ANY(:amenities)
  )
  ```
- [ ] `openNow` 는 KST 현재 시각 + `is_next_day` 처리
- [ ] `sort=distance` 없이 좌표만 있으면 `distance` 컬럼만 추가 (정렬은 유지)

### PR-22: Saved Filter 엔티티 + CRUD

- [ ] `V013__create_saved_filters.sql`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/search/SavedFilter.kt` — `filter_json` 은 `@JdbcTypeCode(SqlTypes.JSON)` 로 `Map<String, Any>` 매핑
- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/search/controller/SavedFilterController.kt`
- [ ] DTO: `SavedFilterCreateRequest`, `SavedFilterResponse`
- [ ] 이름 UNIQUE (활성만), 20개 초과 차단
- [ ] `summaryText` 생성 유틸

### PR-23: Saved Filter 적용 경로

- [ ] `GET /api/v1/places/search?savedFilterId=...` — 서버가 filter_json 로드 + 쿼리 파라미터 overlay
- [ ] `saved_filters.last_applied_at` 갱신
- [ ] 본인 소유 검증 (`SEARCH_004`)

---

## 8. M7 — Notification + Push

### PR-24: Notification 엔티티 + 인앱 기본

- [ ] `V014__create_notifications.sql`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/notification/Notification.kt`
- [ ] `DeviceToken.kt`, `NotificationSetting.kt`
- [ ] `NotificationType` Enum (4종)
- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/notification/controller/NotificationController.kt`
- [ ] GET 목록, 읽음, 삭제, unread-count
- [ ] Redis 캐시 `notifications:unread:{memberId}` TTL 30s

### PR-25: Device Token + 알림 설정

- [ ] `POST|DELETE /api/v1/members/me/device-tokens` — token UPSERT (member 이관)
- [ ] `GET|PATCH /api/v1/members/me/notification-settings`
- [ ] 기본값 생성 로직 (첫 조회 시 row 생성)

### PR-26: FCM / APNs 어댑터

- [ ] `common/src/main/kotlin/com/vivire/locapet/common/push/PushNotifier.kt` — 인터페이스
- [ ] `common/src/main/kotlin/com/vivire/locapet/common/push/FcmPushNotifier.kt` — Firebase Admin SDK
- [ ] `common/src/main/kotlin/com/vivire/locapet/common/push/ApnsPushNotifier.kt` — `com.eatthepath:pushy`
- [ ] `common/src/main/kotlin/com/vivire/locapet/common/push/PushPayload.kt` — 공통 모델
- [ ] 설정: `app.notification.fcm.service-account-json`, `app.notification.apns.*`
- [ ] 실패 재시도 (3회, exponential backoff) + InvalidRegistration → `device_tokens.is_active=FALSE`

### PR-27: 알림 발송 파이프라인 (이벤트 → 인앱 + 푸시)

- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/notification/service/NotificationDispatcher.kt`
- [ ] Outbox Publisher 가 PENDING 이벤트 → 타입별 템플릿 생성 → `notifications` INSERT + 활성 device_tokens 에 FCM/APNs 발송
- [ ] 자기자신 알림 필터, `push_enabled`, `types[type]`, 방해금지 체크
- [ ] Quiet hours 판정: `common/src/main/kotlin/com/vivire/locapet/common/time/QuietHoursUtil.kt`
- [ ] Dedupe key: `REVIEW_LIKED:{reviewId}:{likerId}` 24h UNIQUE

### PR-28: 90일 정리 배치

- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/application/scheduler/NotificationCleanupJob.kt` — `@Scheduled(cron = "0 0 18 * * *")` (= 03:00 KST)
- [ ] 90일 지난 `notifications` hard delete
- [ ] 30일 inactive `device_tokens` 삭제

---

## 9. M8 — Meta 확장 (공지/FAQ/약관)

### PR-29: 공지 확장

- [ ] `V017__extend_notices.sql`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/meta/Notice.kt` 수정 — 신규 컬럼
- [ ] `NoticeCategory`, `NoticePriority`, `NoticeStatus` Enum
- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/meta/controller/NoticeController.kt` — `/api/v1/meta/notices`
- [ ] admin-api 공지 CRUD + publish (Outbox 로 `NOTICE_PUBLISHED` 이벤트 발행 → M7 dispatcher 가 전체 푸시)

### PR-30: FAQ

- [ ] `V015__create_faqs.sql`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/meta/Faq.kt`
- [ ] `FaqCategory` Enum (8종)
- [ ] `/api/v1/meta/faqs`, `/api/v1/meta/faqs/categories`
- [ ] admin-api `faqs` CRUD + reorder
- [ ] Redis 캐시 `faqList::{category}` TTL 1h

### PR-31: ~~Terms 버전 관리 + 동의 이력~~ → **M1.5 PR-04a 로 이동 완료**

> Phase 4 로드맵 검토 결과 **M1.5** 로 앞당김. 본 섹션은 히스토리 목적으로만 유지.
> 이 PR 에서 M8 시점에 남는 작업: **관리자 CMS 연동** — admin-api 의 약관 발행/수정 엔드포인트는 **M10 PR-39** 에서 통합 처리.

### PR-32: 스플래시 통합 (Phase 5 확정 신규)

- [ ] `GET /api/v1/meta/splash?platform=IOS` — 신규 엔드포인트
- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/meta/service/SplashMetaService.kt`
- [ ] 반환: appVersion 최신 + 현재 maintenance + 최신 PINNED/발행 공지 1건 + `pendingTermAgreements` (로그인 시)
- [ ] Redis 캐시 `splash:meta::{platform}` TTL 5분
- [ ] 관리자 변경 시 evict (AppVersion/Maintenance/Notice 수정 hook)

### PR-33: ~~재동의 대기 쓰기 차단 인터셉터~~ → **M1.5 PR-04b 로 이동 완료**

> Phase 4 로드맵 검토 결과 **M1.5** 로 앞당김. 본 섹션은 히스토리 목적으로만 유지.
> M8 시점에 남는 관련 작업: **관리자 약관 발행 시 `terms:pending:*` 일괄 evict hook** — admin CMS 통합 PR (M10 PR-39) 에서 연결.

---

## 10. M9 — Inquiry (Phase 5 확정 SUGGESTION)

### PR-34: Inquiry 엔티티 + CRUD

- [ ] `V018__create_inquiries.sql`
- [ ] `domain/src/main/kotlin/com/vivire/locapet/domain/inquiry/Inquiry.kt`, `InquiryImage.kt`, `InquiryAnswer.kt`
- [ ] `InquiryCategory` Enum (6종, **`SUGGESTION` 포함**)
- [ ] `InquiryStatus` Enum
- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/api/inquiry/controller/InquiryController.kt`
- [ ] POST/GET list/GET detail/PATCH/DELETE/close

### PR-35: Rate Limit

- [ ] `app-api/src/main/kotlin/com/vivire/locapet/app/global/ratelimit/RateLimiter.kt`
- [ ] Redis `inquiry:ratelimit:3m:{memberId}` counter TTL 3분
- [ ] Redis `inquiry:ratelimit:30d:{memberId}` counter TTL 30일
- [ ] 3분/30일 초과 시 `INQUIRY_008` (429)

### PR-36: 답변 등록 (admin-api)

- [ ] `admin-api/src/main/kotlin/com/vivire/locapet/admin/api/inquiry/controller/AdminInquiryController.kt`
- [ ] `POST /admin/api/v1/inquiries/{id}/answer` — `PENDING → ANSWERED`
- [ ] Outbox 이벤트 `INQUIRY_ANSWERED` 발행 → M7 dispatcher 가 인앱 + 푸시

### PR-37: 카테고리 최종 반영 (SUGGESTION)

- [ ] CHECK 제약 `chk_inquiries_category` 에 `SUGGESTION` 포함 확인 (V018 참조)
- [ ] 기존 `FEATURE_REQUEST` 레퍼런스 제거 (문서/코드 전역 검색)
- [ ] 클라이언트 공용 라벨 매핑 제공

---

## 11. M10 — Admin API 완성

### PR-38: admin-api 인증 (ID/PW + IP 화이트리스트) + 초기 seed

- [ ] `admin-api/src/main/kotlin/com/vivire/locapet/admin/global/security/AdminSecurityConfig.kt`
- [ ] `POST /admin/api/v1/auth/login` — ID/PW (bcrypt) + JWT (별도 secret)
- [ ] IP 화이트리스트: `app.admin.ip-whitelist: [...]` 설정 기반 Filter
- [ ] 감사 로그: `admin_audit_logs` 테이블 (신규 V020 or 별도) — 모든 쓰기 요청 기록
- [ ] **초기 seed 관리자 계정 스크립트** (Q4 빠진 항목 보완)
  - `admin-api/src/main/kotlin/com/vivire/locapet/admin/bootstrap/AdminAccountSeeder.kt`
  - `@Profile("!test")` + `ApplicationReadyEvent` 리스너
  - `admins` 테이블에 `admin_count = 0` 일 때만 환경변수 기반 최초 계정 INSERT
  - 환경변수: `ADMIN_SEED_USERNAME`, `ADMIN_SEED_PASSWORD` (Secrets Manager)
  - 시드 완료 후 로그에 "Initial admin account created: {username}" 기록, 비밀번호는 로그 금지
  - Dev 에서 동작: `./gradlew :admin-api:bootRun` 첫 기동 시 환경변수 설정 필수
  - 프로덕션 체크리스트: 초기 기동 후 반드시 비밀번호 변경 + seed env 제거

### PR-39: 관리자 CMS 통합

- [ ] Place / Curation / Notice / FAQ / Terms / AppVersion / Maintenance / Inquiry / Review 관리 엔드포인트 전체 정리
- [ ] 리뷰 신고 대시보드 `GET /admin/api/v1/reviews/reports`
- [ ] 전체 푸시 broadcast `POST /admin/api/v1/notifications/broadcast`
- [ ] 스펙: [14-api-spec.md §12](./14-api-spec.md)

---

## 12. M11 — 운영 안정화

### PR-40: 야간 배치 정리

- [ ] `FavoriteCountRecalcJob` (02:00 KST) — Place 집계 재계산
- [ ] `ReviewStatsRecalcJob` (02:30 KST) — rating_avg / review_count / like_count 재계산
- [ ] `NotificationCleanupJob` (03:00 KST) — 90일 정리
- [ ] `WithdrawalFinalizeJob` (03:30 KST) — 30일 경과 회원 hard delete 처리
- [ ] `OutboxCleanupJob` — 90일 지난 PUBLISHED 이벤트 삭제
- [ ] `UploadedMediaCleanupJob` — 24h 이상 uncommitted 이미지 S3 삭제

### PR-41: 캐시 무효화 전략

- [ ] 관리자 Place 수정 → `placeDetail::{id}` evict + `wishlist:trending` 은 무관
- [ ] 공지 발행 → `splash:meta::*` evict
- [ ] 약관 발행 → `terms:pending:*` + `terms:current` evict
- [ ] FAQ CRUD → `faqList::*` evict
- [ ] 패턴: `@CacheEvict` 또는 명시적 `RedisTemplate.delete(pattern)`

### PR-42: 모니터링

- [ ] Spring Actuator `/actuator/health`, `/actuator/metrics`
- [ ] 로그 JSON 포맷 통일 (Logstash 연동용)
- [ ] 에러 코드별 메트릭 집계 (`ErrorResponse.errorCode` 태그)
- [ ] FCM/APNs 발송 성공률 대시보드

### PR-43: 부하 테스트

- [ ] k6 또는 Gatling 스크립트 (`scripts/load-tests/`)
- [ ] 주요 시나리오:
  - 업체 검색 (1000 RPS, p95 < 500ms)
  - 반경 검색 (500 RPS, p95 < 500ms)
  - 알림 목록 (500 RPS)
  - 공지/스플래시 (5000 RPS, 캐시 효과)
- [ ] 대용량 INSERT broadcast 시뮬레이션

---

## 13. 테스트 전략

### 13.1 통합 테스트 (Testcontainers)

- 기반: `IntegrationTestSupport` (현재 오타 파일명 `IntergrationTestSupport.kt` — Phase 5 리팩터 기회)
- PostgreSQL(postgis) + Redis 자동 기동
- 각 도메인 PR 마다 **Controller → Service → DB 라운드트립** 1건 이상
- 예:
  - `app-api/src/test/kotlin/com/vivire/locapet/app/api/pet/PetControllerIntegrationTest.kt`
  - `app-api/src/test/kotlin/com/vivire/locapet/app/api/review/ReviewControllerIntegrationTest.kt`
  - `app-api/src/test/kotlin/com/vivire/locapet/app/api/place/PlaceSearchIntegrationTest.kt` (PostGIS 반경검색)

### 13.2 단위 테스트

- Service 레이어: Mockito 기반. 비즈니스 규칙 (hasPet 분기, 대표 Pet 자동 승격 등)
- Util: `GeoUtil`, `QuietHoursUtil`, `CursorCodec`

### 13.3 커버리지 목표

- Service 레이어: 80%+
- Controller 레이어: 스모크 테스트만 (통합 테스트로 커버)
- DB 레이어: Repository 는 통합 테스트에서만 검증

### 13.4 CI

기존 `.github/workflows/ci.yml` 유지 + Phase 5 이후 **테스트 실행 시 PostGIS 이미지 사용** 확인.

### 13.5 이벤트 Dedupe Key 컨벤션 (Q4 빠진 항목 보완)

Outbox Publisher + NotificationDispatcher 가 **24h 내 동일 이벤트 중복 발송**을 방지한다. 저장소는 **Redis** (TTL 관리 자동화 + TTL=24h+α).

#### Key 네이밍 규칙

```
notif:dedupe:{eventType}:{primaryId}[:{actorId}]
```

- `primaryId`: 이벤트의 주대상 리소스 ID (예: reviewId, noticeId, inquiryId)
- `actorId`: 행위자 별로 dedupe 해야 하는 경우만 (예: 좋아요를 여러 명이 누를 때 각자 1회씩 발송)
- 실제 값은 `SET key "1" EX 86400 NX` — `NX` 로 중복 즉시 검출

#### MVP 적용 케이스 (타입별)

| 이벤트 타입 | Key | 의미 |
|---|---|---|
| `REVIEW_LIKED` | `notif:dedupe:REVIEW_LIKED:{reviewId}:{likerId}` | 동일인이 24h 내 같은 리뷰에 like → unlike → like 반복해도 알림 1회 |
| `ANNOUNCEMENT` | `notif:dedupe:ANNOUNCEMENT:{noticeId}` | 관리자가 같은 공지를 재발송해도 24h 내 1회 |
| `INQUIRY_ANSWERED` | `notif:dedupe:INQUIRY_ANSWERED:{inquiryId}` | 답변 수정 시 재발송 안 함 (10-announcement-spec 및 11-inquiry-spec 정책 일치) |
| `SYSTEM` (maintenance) | `notif:dedupe:SYSTEM:MAINT:{maintenanceId}` | 점검 공지 재발송 차단 |

#### 구현 위치

- `common/src/main/kotlin/com/vivire/locapet/common/notification/NotificationDedupeGuard.kt`
- `NotificationDispatcher` 가 각 수신자별 발송 직전 `guard.claim(eventType, primaryId, actorId?)` 호출 → `false` 반환 시 skip
- **NOTE**: 이 Dedupe 는 **푸시 발송** 과 **인앱 저장** 각각에 적용. 정책에 따라 인앱은 dedupe 완화(여러번 저장)도 가능하나 **MVP 는 동일 정책** (1회만).

#### 장애 대응

- Redis 다운 → guard 는 기본값 `true` 반환 (발송 허용) + ERROR 로그. 중복 발송 < 미발송.
- `SYSTEM` 점검 공지는 수동 broadcast 이므로 관리자 UX 에서 "재발송" 토글 제공 (M10 PR-39).

#### 관련 PR

- **PR-04**: `NotificationDedupeGuard` 스텁 구현 (실제 체크는 M7)
- **PR-27**: NotificationDispatcher 에서 실제 적용

---

## 14. 배포 / 롤아웃 순서

### 14.1 Dev 환경

- M2 ~ M4 완료 시점에 `release-dev-phase5-m4` 태그로 첫 배포
- 이후 마일스톤 완료마다 `release-dev-phase5-m{N}` 태그

### 14.2 Staging 전환 조건

- M9 완료 (Inquiry 까지) + M10 Admin 기본 완성
- QA 라운드 1회 통과 (핵심 UC1~UC7 검증)
- `release-staging-phase5-mvp-rc1`

### 14.3 Prod 승인 체크리스트

- [ ] 부하 테스트 결과 목표 달성
- [ ] PASS API 운영 키 세팅 + 모의 거래 성공
- [ ] FCM/APNs 운영 키 + dev 테스트 발송 성공
- [ ] S3 + CloudFront 운영 도메인 준비
- [ ] 개인정보 처리방침 / 이용약관 최신 버전 DB 등록
- [ ] 관리자 계정 초기화 (최소 1개)
- [ ] IP 화이트리스트 설정
- [ ] 모니터링 대시보드 green

---

## 15. 위험 / 리스크

| 리스크 | 영향 | 완화 |
|---|---|---|
| PostGIS 확장 운영 경험 부족 | Place/검색 장애 | 로컬/Testcontainers 에서 충분한 쿼리 검증. 인덱스 GIST + 부분 인덱스 조합 모니터링 |
| FCM/APNs 안정성 | 알림 누락 | Outbox + 재시도 + InvalidRegistration 처리. Dev 에서 실기기 테스트 |
| 네이버지도 API 쿼터 | 지오코딩 비용 | 관리자 입력 단계에서 1회만 지오코딩 + 결과 캐시 |
| 대용량 broadcast | DB 스파이크 | INSERT SELECT 배치 + 청크 푸시 (1000건/회). 야간 발송 권장 |
| PostGIS 이미지 교체 중 마이그레이션 | 기존 데이터 영향 | Dev 에서 먼저 검증. V001~V004 는 PostGIS 이미지에서도 동일 동작 |
| JPA + geography 매핑 | Hibernate Spatial 버전 불일치 | Spring Boot 4 + Hibernate 6 에서 검증된 조합 사용 |
| 약관 재동의 쓰기 차단 구현 복잡도 | 버그로 정상 요청까지 차단 | HandlerInterceptor + 캐시 + 화이트리스트 테스트 세트 필수 |

---

## 16. PR 체크 템플릿 (참고)

각 PR 에 공통으로 확인할 항목:

```markdown
## 요약
- [ ] 관련 마이그레이션: V0XX
- [ ] 엔티티/Repository 추가
- [ ] Controller/Service 구현
- [ ] DTO 정의 (Request/Response)
- [ ] 에러 코드 추가 (`{DOMAIN}_{NNN}`)
- [ ] SecurityConfig 경로 업데이트 (필요 시)

## 테스트
- [ ] 통합 테스트 1건 이상
- [ ] curl 검증 시나리오 제시
- [ ] 에러 케이스 포함

## 영향도
- [ ] 기존 엔티티 수정 여부
- [ ] Cache evict 필요 여부
- [ ] 새 application.yml 키 여부

## 문서
- [ ] 14-api-spec.md 반영 확인
- [ ] 15-db-schema.md 반영 확인
```

---

## 17. 관련 문서

- [14. API 스펙](./14-api-spec.md)
- [15. DB 스키마](./15-db-schema.md)
- [03. 공통 정책](./03-common-policies.md)
- [CLAUDE.md](../../.claude/CLAUDE.md)
