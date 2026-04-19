# 10. 공지사항 · FAQ · 약관(Announcement / FAQ / Terms) 기능 명세

> 본 문서는 로카펫의 **D2. 메타(Meta)** 도메인 중 **공지사항 · 자주 묻는 질문(FAQ) · 약관/정책** 영역 상세 기획이다.
> 앱 버전 · 유지보수 · 스플래시 메타는 기존 구현(`AppVersion`, `Maintenance`) 을 유지하며, 본 문서의 범위 밖이다.
> 상위 문서: [01. 도메인 맵](./01-domain-map.md), [02. 용어집](./02-glossary.md), [03. 공통 정책](./03-common-policies.md)

---

## 1. 개요

### 1.1 도메인 책임

- **공지사항(Notices)** — 앱 내에서 노출되는 운영 공지. 기존 `notices` 엔티티를 Figma 요구에 맞게 확장한다.
- **FAQ** — 카테고리별 질문/답변. 비로그인 조회 허용. 관리자가 admin-api 로 관리.
- **약관/정책(Terms)** — 이용약관 · 개인정보처리방침 · 위치정보 이용약관 · 마케팅 수신 동의. **버전 관리** + **회원 동의 이력**.
- 모든 리소스는 **admin-api CMS** 로 관리되며, **조회는 app-api 공개 API** (비로그인 허용).

### 1.2 다른 도메인과의 관계

| 방향 | 대상 도메인 | 관계 |
|---|---|---|
| 참조 | Member(D3) | `member_term_agreements.member_id` → FK |
| 생산 (이벤트) | Notification(D11) | 공지 발행 시 `ANNOUNCEMENT` 알림 발송 옵션 |
| 소비 | Auth(D1) | 온보딩 약관 동의 플로우 — 본인인증 후 프로필 완성 시 약관 동의 이력 기록 |
| 독립 | - | 앱 버전/유지보수와는 API 경로만 공유. 데이터 독립 |

### 1.3 기존 구현 재활용

- **`notices` 엔티티** (`domain/src/main/kotlin/com/vivire/locapet/domain/meta/Notice.kt`) — 이미 존재. 본 문서에서 확장 필드 제안.
- **`AppVersion`, `Maintenance`** — 변경 없음. 스플래시 메타 경로 유지.
- **약관 동의 플래그** (`termsOfServiceAgreed`, `privacyPolicyAgreed`, `marketingConsent`) — Member 엔티티에 이미 존재. 본 문서에서 **버전 관리된 약관 + 동의 이력 테이블**로 확장.

---

## 2. 엔티티 & 데이터 모델

### 2.1 `notices` (공지사항) — 기존 확장

**현재 추정 스키마** (기존): `id, title, content, created_at` 수준.

**확장 제안 스키마**:

| 컬럼 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `id` | BIGSERIAL | O | - | PK |
| `title` | VARCHAR(200) | O | - | 제목 |
| `content` | TEXT | O | - | 본문 (plain text + 줄바꿈 보존) |
| `category` | VARCHAR(30) | O | `GENERAL` | `GENERAL` / `UPDATE` / `EVENT` / `URGENT` (Enum) |
| `priority` | VARCHAR(10) | O | `NORMAL` | `NORMAL` / `PINNED` (상단 고정) |
| `thumbnail_image_url` | VARCHAR(500) | X | NULL | 목록 썸네일 |
| `status` | VARCHAR(20) | O | `DRAFT` | `DRAFT` / `PUBLISHED` / `ARCHIVED` |
| `start_at` | TIMESTAMPTZ | X | NULL | 노출 시작 (미래 발행) |
| `end_at` | TIMESTAMPTZ | X | NULL | 노출 종료 (자동 아카이브) |
| `published_at` | TIMESTAMPTZ | X | NULL | 최초 발행 시각 |
| `send_push` | BOOLEAN | O | FALSE | 발행 시 푸시 알림 발송 여부 |
| `pushed_at` | TIMESTAMPTZ | X | NULL | 푸시 발송 완료 시각 |
| `created_at` | TIMESTAMPTZ | O | NOW() | |
| `updated_at` | TIMESTAMPTZ | O | NOW() | |
| `deleted_at` | TIMESTAMPTZ | X | NULL | 소프트 삭제 |

> 기존 `notices` 에 필드가 적게 있다면 Flyway 마이그레이션 (`V00X__extend_notices_table.sql`) 으로 `ADD COLUMN` 처리. 기존 row 는 기본값으로 채움.

### 2.2 `faqs`

| 컬럼 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `id` | BIGSERIAL | O | - | PK |
| `category` | VARCHAR(30) | O | - | Enum (§2.6) |
| `question` | VARCHAR(200) | O | - | 질문 |
| `answer` | TEXT | O | - | 답변 (plain text + 줄바꿈) |
| `display_order` | INT | O | 0 | 카테고리 내 정렬 우선순위 (작을수록 상단) |
| `status` | VARCHAR(20) | O | `DRAFT` | `DRAFT` / `PUBLISHED` |
| `created_at` | TIMESTAMPTZ | O | NOW() | |
| `updated_at` | TIMESTAMPTZ | O | NOW() | |
| `deleted_at` | TIMESTAMPTZ | X | NULL | |

### 2.3 `terms` (약관 마스터 — 버전 관리)

| 컬럼 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `id` | BIGSERIAL | O | - | PK |
| `code` | VARCHAR(30) | O | - | Enum (§2.7) — 약관 종류 |
| `version` | VARCHAR(20) | O | - | 버전 문자열 (예: `1.0`, `2026-04-18`) |
| `title` | VARCHAR(200) | O | - | 제목 |
| `content` | TEXT | O | - | 본문 (HTML 또는 plain — §4.3) |
| `is_required` | BOOLEAN | O | TRUE | 필수 동의 여부 (FALSE = 선택 — 마케팅 등) |
| `effective_from` | TIMESTAMPTZ | O | NOW() | 적용 시작 시각 |
| `effective_to` | TIMESTAMPTZ | X | NULL | 적용 종료 — 신규 버전 발행 시 설정 |
| `status` | VARCHAR(20) | O | `DRAFT` | `DRAFT` / `PUBLISHED` / `ARCHIVED` |
| `created_at` | TIMESTAMPTZ | O | NOW() | |
| `updated_at` | TIMESTAMPTZ | O | NOW() | |

**UNIQUE** `(code, version)` — 같은 약관의 동일 버전 중복 금지.
**UNIQUE (부분)** `CREATE UNIQUE INDEX uk_terms_code_current ON terms (code) WHERE status = 'PUBLISHED' AND effective_to IS NULL` — 약관 종류당 **현재 유효 버전은 1개** 보장.

### 2.4 `member_term_agreements` (동의 이력)

| 컬럼 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `id` | BIGSERIAL | O | - | PK |
| `member_id` | BIGINT | O | - | FK → members.id |
| `term_id` | BIGINT | O | - | FK → terms.id (버전 특정) |
| `agreed_at` | TIMESTAMPTZ | O | NOW() | 동의 시각 |
| `ip_address` | VARCHAR(45) | X | NULL | IPv4/IPv6 — 감사 로그용 |
| `user_agent` | VARCHAR(300) | X | NULL | |
| `withdrawn_at` | TIMESTAMPTZ | X | NULL | 동의 철회 시각 (마케팅 옵트아웃 등) |

- UNIQUE `(member_id, term_id)` — 같은 약관 버전에 대해 1 회원 1 동의.
- INDEX `idx_term_agreements_member (member_id)` — 내 동의 이력 조회.
- INDEX `idx_term_agreements_term (term_id)` — 약관별 동의 수 집계.

### 2.5 Enum — 공지 카테고리

```kotlin
enum class NoticeCategory { GENERAL, UPDATE, EVENT, URGENT }
enum class NoticePriority { NORMAL, PINNED }
enum class NoticeStatus { DRAFT, PUBLISHED, ARCHIVED }
```

### 2.6 Enum — FAQ 카테고리

```kotlin
enum class FaqCategory {
    ACCOUNT,     // 계정/로그인
    PET,         // 반려동물 등록
    PLACE,       // 업체/검색
    REVIEW,      // 리뷰
    FAVORITE,    // 찜/저장
    NOTIFICATION,// 알림/푸시
    PAYMENT,     // 결제 (미래)
    ETC          // 기타
}
```

- Figma [자주 묻는 질문](node-id=479:26501) 의 카테고리 탭 구성을 기반으로 8종 제안.

### 2.7 Enum — 약관 코드

```kotlin
enum class TermCode {
    SERVICE_TERMS,       // 이용약관 (필수)
    PRIVACY_POLICY,      // 개인정보 처리방침 (필수)
    LOCATION_TERMS,      // 위치정보 이용약관 (필수)
    MARKETING_CONSENT,   // 마케팅 수신 동의 (선택)
    PUSH_CONSENT         // 앱 푸시 동의 (선택 — iOS 기본, Android 13+ 런타임)
}
```

### 2.8 상태 머신 — 공지

```
   create         publish (start_at 도달)
 ┌───────┐      ┌───────────┐
 │ DRAFT │─────▶│ PUBLISHED │───────┐
 └───────┘      └─────┬─────┘       │ end_at 도달 or 관리자 아카이브
                      │             ▼
                      │       ┌──────────┐
                      │       │ ARCHIVED │
                      │       └──────────┘
                      │ soft delete
                      ▼
                ┌──────────┐
                │ DELETED  │ (deleted_at)
                └──────────┘
```

- `start_at` 미지정 → 즉시 노출. 지정 시 예약 발행(스케줄러).
- `end_at` 도달 시 자동 `ARCHIVED` (배치 or on-read 필터).

### 2.9 상태 머신 — 약관

```
  create         publish      supersede (신규 버전 발행)
 ┌───────┐     ┌───────────┐  effective_to=NOW  ┌──────────┐
 │ DRAFT │────▶│ PUBLISHED │────────────────────▶│ ARCHIVED │
 └───────┘     └───────────┘                     └──────────┘
```

- 같은 `code` 의 신규 버전 발행 시:
  1. 기존 current 레코드의 `effective_to = NOW()`, `status = ARCHIVED`.
  2. 신규 레코드 `status = PUBLISHED`, `effective_to IS NULL`.
  3. 기존 동의 이력은 그대로 유지 — 회원은 **재동의 필요** (로그인 시 체크).

---

## 3. 주요 기능 (Use Case)

### 3.1 공지사항 목록 조회

- **대상 화면**: [공지사항 목록](node-id=479:26467), [공지사항 Empty](node-id=479:26486)

- **엔드포인트**: `GET /api/v1/notices?cursor=...&size=20&category=ALL`
- **동작**:
  - `status = 'PUBLISHED' AND deleted_at IS NULL`.
  - `start_at IS NULL OR start_at <= NOW()`.
  - `end_at IS NULL OR end_at > NOW()`.
  - 정렬: `priority DESC (PINNED 먼저), published_at DESC, id DESC`.
- **응답 아이템**:
  ```json
  {
    "id": 15,
    "title": "v1.2.0 업데이트 안내",
    "category": "UPDATE",
    "priority": "PINNED",
    "thumbnailImageUrl": "https://cdn.locapet.app/notices/15/thumb.jpg",
    "publishedAt": "2026-04-15T00:00:00Z"
  }
  ```
- **권한**: 비로그인 허용.

### 3.2 공지 상세 조회

- **대상 화면**: [공지 내용](node-id=479:26492), [복제본](node-id=479:28896)

- **엔드포인트**: `GET /api/v1/notices/{id}`
- **응답**:
  ```json
  {
    "id": 15,
    "title": "v1.2.0 업데이트 안내",
    "content": "이번 업데이트에서는...\n...",
    "category": "UPDATE",
    "priority": "PINNED",
    "thumbnailImageUrl": "...",
    "publishedAt": "2026-04-15T00:00:00Z"
  }
  ```
- **에러**: 없거나 미발행 → `NOTICE_001` (404).
- **권한**: 비로그인 허용.
- **캐시**: `noticeDetail::{id}` TTL 10분. 관리자 수정 시 evict.

### 3.3 공지 발행 — 관리자 + 이벤트

- **엔드포인트**: `POST /admin/api/v1/notices/{id}/publish`
- **동작**:
  1. `status = DRAFT → PUBLISHED`, `published_at = NOW()` (최초만).
  2. `send_push = TRUE` 이면 `NoticePublishedEvent` 발행 → Notification 도메인이 `ANNOUNCEMENT` 타입으로 broadcast ([09-notification-spec.md §3.9](./09-notification-spec.md)).
  3. `pushed_at` 갱신.
- **권한**: 관리자.

### 3.4 FAQ 목록 조회

- **대상 화면**: [자주 묻는 질문](node-id=479:26501), [FAQ 헤더](node-id=479:47146)

- **엔드포인트**: `GET /api/v1/faqs?category=ACCOUNT&q=로그인`
- **동작**:
  - `status = 'PUBLISHED' AND deleted_at IS NULL`.
  - `category` 미지정 시 전체, 지정 시 해당 카테고리만.
  - `q` 있으면 `question ILIKE '%q%' OR answer ILIKE '%q%'`.
  - 정렬: `category ASC, display_order ASC, id ASC`.
- **응답**:
  ```json
  {
    "items": [
      {
        "id": 101,
        "category": "ACCOUNT",
        "question": "비밀번호를 잊어버렸어요.",
        "answer": "소셜 로그인만 지원하므로 비밀번호가 없어요...",
        "displayOrder": 0
      }
    ]
  }
  ```
- **페이지네이션**: FAQ 총량이 적을 것으로 예상 — **Offset 기반** (공통 정책 §4.1 예외 항목).
- **권한**: 비로그인 허용.
- **캐시**: `faqList::{category}` TTL 1시간.

### 3.5 FAQ 카테고리 탭 구성 조회

- **엔드포인트**: `GET /api/v1/faqs/categories`
- **응답**: 카테고리 Enum + 각 개수
  ```json
  {
    "categories": [
      { "code": "ACCOUNT", "label": "계정", "count": 5 },
      { "code": "PET", "label": "반려동물", "count": 3 }
    ]
  }
  ```
- **권한**: 비로그인 허용. 1시간 캐시.

### 3.6 약관 목록 조회 (앱 내 "약관 및 정책")

- **대상 화면**: [약관 및 정책](node-id=479:28774), [약관 상세 1](node-id=479:28780), [약관 상세 2](node-id=479:28806)

- **엔드포인트**: `GET /api/v1/terms?code=SERVICE_TERMS` (단건) / `GET /api/v1/terms` (모두 현재 유효 버전)
- **동작**:
  - 현재 유효 버전만 반환 (`status='PUBLISHED' AND effective_to IS NULL`).
  - `code` 파라미터로 필터.
- **응답**:
  ```json
  {
    "items": [
      {
        "id": 3,
        "code": "SERVICE_TERMS",
        "version": "1.2",
        "title": "이용약관",
        "effectiveFrom": "2026-04-01T00:00:00Z",
        "isRequired": true
      }
    ]
  }
  ```
- **권한**: 비로그인 허용.

### 3.7 약관 본문 조회

- **엔드포인트**: `GET /api/v1/terms/{id}`
- **응답**: 위 스키마 + `content`.
- **캐시**: `terms::{id}` TTL 24시간 (거의 불변).

### 3.8 약관 동의 — 온보딩 통합

- **사전조건**: 온보딩 `PROFILE_REQUIRED` 단계 (`onboardingAccessToken` 사용).
- **엔드포인트**: `POST /api/v1/onboarding/profile/complete` 에 통합 (기존 플로우).
- **입력 확장**:
  ```json
  {
    "nickname": "초코맘",
    "termAgreements": [
      { "termId": 3, "agreed": true },
      { "termId": 4, "agreed": true },
      { "termId": 5, "agreed": false }
    ]
  }
  ```
- **동작**:
  1. `terms` 에서 `is_required = TRUE` 인 현재 유효 버전 조회.
  2. 요청에 **필수 약관 전부 `agreed=true`** 포함 여부 검증. 누락 시 `TERMS_001` (400).
  3. `member_term_agreements` 다건 INSERT (`member_id`, `term_id`, `agreed_at=NOW()`, `ip_address`, `user_agent`).
  4. `members.terms_of_service_agreed`, `privacy_policy_agreed`, `marketing_consent` 기존 플래그도 함께 갱신 (레거시 호환).
- **권한**: `onboardingAccessToken` 필수.

### 3.9 약관 재동의 — 신규 버전 발행 후

- **판정**: 로그인 직후 서버가 체크:
  ```sql
  SELECT t.*
    FROM terms t
   WHERE t.status = 'PUBLISHED'
     AND t.effective_to IS NULL
     AND t.is_required = TRUE
     AND NOT EXISTS (
         SELECT 1 FROM member_term_agreements mta
          WHERE mta.member_id = :memberId
            AND mta.term_id = t.id
     );
  ```
- **응답**: 정상 로그인 응답에 `pendingTermAgreements: [...]` 필드 추가.
- **동작**: 클라이언트가 재동의 UI 노출 → `POST /api/v1/members/me/term-agreements` 로 제출.

### 3.10 추가 동의 / 철회

- **엔드포인트**:
  - `POST /api/v1/members/me/term-agreements` — 신규 동의 (재동의, 마케팅 등)
  - `DELETE /api/v1/members/me/term-agreements/{termId}` — 철회 (선택 약관만) → `withdrawn_at = NOW()` 세팅
- **입력**:
  ```json
  {
    "agreements": [
      { "termId": 6, "agreed": true }
    ]
  }
  ```
- **규칙**:
  - 필수 약관(`is_required=TRUE`)은 철회 불가 (`TERMS_003` 403).
  - 이미 동의한 버전 재동의 시 idempotent 200.
- **권한**: accessToken 필수.

### 3.11 내 동의 이력 조회

- **엔드포인트**: `GET /api/v1/members/me/term-agreements`
- **응답**: 회원이 동의한 약관 버전 이력 전부.
- **권한**: accessToken 필수.

### 3.12 관리자 CMS — 리소스 관리 (간략)

| Method | Path | 설명 |
|---|---|---|
| POST/PATCH/DELETE | `/admin/api/v1/notices[/{id}]` | 공지 CRUD |
| POST | `/admin/api/v1/notices/{id}/publish` | 발행 + push 이벤트 |
| POST/PATCH/DELETE | `/admin/api/v1/faqs[/{id}]` | FAQ CRUD |
| PUT | `/admin/api/v1/faqs/reorder` | 카테고리 내 순서 일괄 변경 |
| POST/PATCH | `/admin/api/v1/terms[/{id}]` | 약관 CRUD |
| POST | `/admin/api/v1/terms/{id}/publish` | 약관 발행 (기존 버전 archive + 이벤트 발행) |

---

## 4. 비즈니스 규칙 & 제약

### 4.1 도메인 불변식

1. **공지는 `PUBLISHED` + `start_at ≤ NOW` + (`end_at IS NULL OR end_at > NOW`) 조건을 모두 만족해야 공개 노출**.
2. **`PINNED` 공지는 목록 최상단**. 여러 개면 `published_at DESC`.
3. **FAQ 는 카테고리 내 `display_order ASC` 정렬**. 동순위는 `id ASC`.
4. **약관 종류당 현재 유효 버전은 1개** — UNIQUE 부분 인덱스 물리 보장.
5. **필수 약관 미동의 시 온보딩 완료 불가** (`TERMS_001`).
6. **신규 약관 버전 발행 시 기존 동의는 유효하지 않음** — 재동의 유도.
7. **필수 약관 철회 불가** — `is_required = TRUE` 면 `DELETE` 거부.
8. **약관 동의 이력은 불변** (`agreed_at`, `ip_address`, `user_agent`). 철회는 `withdrawn_at` 으로 별도 기록.
9. **공지 발행 시 `send_push=TRUE` 면 일회성 broadcast** — 수정 후 재발송은 명시적으로 호출해야 함.

### 4.2 입력 제약

| 필드 | 제약 |
|---|---|
| 공지 `title` | 1~200자 |
| 공지 `content` | 최대 20000자 |
| 공지 `category` | Enum 값만 |
| FAQ `question` | 1~200자 |
| FAQ `answer` | 1~10000자 |
| FAQ `display_order` | 0~9999 |
| 약관 `title` | 1~200자 |
| 약관 `content` | 최대 100000자 |
| 약관 `version` | 1~20자, semver 또는 ISO 날짜 권장 |
| `ip_address` | IPv4/IPv6 — 서버에서 추출 (클라이언트 신뢰 금지) |

### 4.3 컨텐츠 포맷 정책

- **공지 본문 · FAQ 답변** — MVP 는 **plain text + `\n` 줄바꿈 보존**. 마크다운 미지원. 링크는 URL 자동 감지(클라이언트 책임).
- **약관 본문** — MVP 는 plain text 또는 **제한된 HTML** (`<p>`, `<br>`, `<ul>`, `<li>`, `<a>`, `<strong>`) 을 관리자가 선택. XSS 방어를 위해 **서버 측 sanitize** 필수 (Jsoup Safelist).
- Phase 4+ 에서 마크다운 렌더링 또는 리치 에디터 도입 검토.

### 4.4 이미지

- 공지 썸네일만 단일 이미지 (1장, 400x400 권장). [03-common-policies.md §5](./03-common-policies.md) Presigned 업로드 플로우.

### 4.5 기존 `Member` 약관 플래그와의 관계

기존 Member 엔티티는 온보딩 시 기록된 boolean 플래그를 가짐:
- `terms_of_service_agreed: Boolean`
- `privacy_policy_agreed: Boolean`
- `marketing_consent: Boolean`

**정책**:
- 이 플래그들은 **"가장 최근 동의 상태"의 캐시** 로 해석한다.
- 진실의 원본은 `member_term_agreements` + `terms` 조인.
- 로그인 시 재동의 필요 여부도 조인으로 판정 (§3.9).
- 기존 코드 호환을 위해 플래그는 유지하되, 신규 로직은 `member_term_agreements` 조회 우선.

---

## 5. API 엔드포인트 초안 (REST)

### 5.1 app-api (공개/회원)

| Method | Path | Auth | 설명 |
|---|---|:---:|---|
| GET | `/api/v1/notices` | optional | 공지 목록 |
| GET | `/api/v1/notices/{id}` | optional | 공지 상세 |
| GET | `/api/v1/faqs` | optional | FAQ 목록 (카테고리/키워드 필터) |
| GET | `/api/v1/faqs/categories` | optional | 카테고리 탭 구성 |
| GET | `/api/v1/terms` | optional | 현재 유효 약관 목록 |
| GET | `/api/v1/terms/{id}` | optional | 약관 본문 |
| GET | `/api/v1/members/me/term-agreements` | accessToken | 내 동의 이력 |
| POST | `/api/v1/members/me/term-agreements` | accessToken | 신규/재동의 |
| DELETE | `/api/v1/members/me/term-agreements/{termId}` | accessToken | 선택 약관 철회 |

### 5.2 admin-api (관리자)

| Method | Path | 설명 |
|---|---|---|
| POST/GET/PATCH/DELETE | `/admin/api/v1/notices[/{id}]` | 공지 CRUD |
| POST | `/admin/api/v1/notices/{id}/publish` | 발행 |
| POST | `/admin/api/v1/notices/{id}/archive` | 아카이브 |
| POST/GET/PATCH/DELETE | `/admin/api/v1/faqs[/{id}]` | FAQ CRUD |
| PUT | `/admin/api/v1/faqs/reorder` | 순서 일괄 변경 |
| POST/GET/PATCH | `/admin/api/v1/terms[/{id}]` | 약관 CRUD |
| POST | `/admin/api/v1/terms/{id}/publish` | 약관 발행 |

### 5.3 응답 예시

#### `GET /api/v1/notices`

```json
{
  "items": [
    {
      "id": 15,
      "title": "v1.2.0 업데이트 안내",
      "category": "UPDATE",
      "priority": "PINNED",
      "thumbnailImageUrl": "https://cdn.locapet.app/notices/15/thumb.jpg",
      "publishedAt": "2026-04-15T00:00:00Z"
    }
  ],
  "nextCursor": "eyJpZCI6MTR9",
  "hasNext": true
}
```

#### `GET /api/v1/terms` (현재 유효)

```json
{
  "items": [
    { "id": 3, "code": "SERVICE_TERMS", "version": "1.2", "title": "이용약관", "effectiveFrom": "2026-04-01T00:00:00Z", "isRequired": true },
    { "id": 4, "code": "PRIVACY_POLICY", "version": "1.1", "title": "개인정보 처리방침", "effectiveFrom": "2026-03-15T00:00:00Z", "isRequired": true },
    { "id": 5, "code": "LOCATION_TERMS", "version": "1.0", "title": "위치정보 이용약관", "effectiveFrom": "2026-01-01T00:00:00Z", "isRequired": true },
    { "id": 6, "code": "MARKETING_CONSENT", "version": "1.0", "title": "마케팅 정보 수신 동의", "effectiveFrom": "2026-01-01T00:00:00Z", "isRequired": false }
  ]
}
```

---

## 6. 에러 코드

| 코드 | HTTP | 메시지 | 발생 상황 |
|---|:---:|---|---|
| `NOTICE_001` | 404 | 공지사항을 찾을 수 없어요. | 없는 id, 미발행, 종료 |
| `NOTICE_002` | 400 | 공지사항 카테고리가 올바르지 않아요. | Enum 외 값 |
| `FAQ_001` | 404 | FAQ 를 찾을 수 없어요. | 없는 id, 미발행 |
| `FAQ_002` | 400 | FAQ 카테고리가 올바르지 않아요. | Enum 외 값 |
| `TERMS_001` | 400 | 필수 약관에 모두 동의해주세요. | 온보딩 완료 요청에서 필수 약관 누락 |
| `TERMS_002` | 404 | 해당 약관을 찾을 수 없어요. | 없는 termId |
| `TERMS_003` | 403 | 필수 약관은 철회할 수 없어요. | 필수 약관 DELETE 시도 |
| `TERMS_004` | 409 | 이미 동일 버전의 약관에 동의하셨어요. | 재동의 중복 (또는 idempotent 200 처리 제안 ❓) |
| `TERMS_005` | 400 | 약관 버전이 유효하지 않아요. | archive 된 버전에 동의 시도 |

---

## 7. 엣지 케이스 & 예외 처리

| 케이스 | 처리 |
|---|---|
| **예약 발행된 공지가 `start_at` 도달** | 배치 없이 **on-read 필터** 로 노출. `SELECT WHERE start_at <= NOW()`. 푸시 발송은 별도 스케줄러 필요 (`pushed_at IS NULL AND send_push AND start_at <= NOW()`). |
| **종료된 공지 상세 직접 URL 접근** | 404 (`NOTICE_001`). 관리자만 admin-api 에서 archive 상태 조회 가능. |
| **약관 신규 버전 발행 직후 진행 중인 온보딩 세션** | `onboardingAccessToken` 유효 기간(30분) 내 제출 시 신규 버전 기준으로 검증 → 구 버전 동의만 있으면 `TERMS_001`. |
| **필수 약관 재동의 대기 중 API 호출** | 로그인 응답에 `pendingTermAgreements` 존재 시 **중요 API(리뷰 작성/찜 등)는 서버가 409 or 특정 코드로 차단** ❓ — MVP 제안: 조회 API 는 허용, 쓰기 API 는 재동의 유도. 구체 정책 확인 필요. |
| **FAQ 카테고리가 없어도 검색 결과 나와야 함** | `category` 미지정 + `q` 만 있는 경우 전체 PUBLISHED FAQ 에서 검색. |
| **공지 썸네일 이미지 업로드 실패로 NULL** | 클라이언트가 카테고리별 기본 이미지 fallback. |
| **`send_push=TRUE` 인데 푸시 발송 장애** | `pushed_at` 은 NULL 유지. 관리자가 재시도 API 호출 가능. |
| **탈퇴 완료 회원의 약관 동의 이력** | [03-common-policies.md §2.1](./03-common-policies.md) — **유지** (감사 로그). 이름/연락처는 마스킹되어도 동의 이력은 법적 근거로 남김. |
| **같은 공지를 여러 번 push 발송 시도** | `pushed_at IS NOT NULL` 이면 409 + 강제 플래그 있어야 재발송. |
| **약관 본문에 XSS 페이로드 관리자 입력** | 저장 시 Jsoup sanitize. 출력 시 Content-Security-Policy 헤더. |

---

## 8. 성능 / 인덱싱 고려사항

### 8.1 주요 쿼리 패턴

| 패턴 | 빈도 | 인덱스 |
|---|:---:|---|
| 공지 목록 (priority DESC, published_at DESC) | 높음 | `idx_notices_status_published (status, priority DESC, published_at DESC) WHERE deleted_at IS NULL` |
| 공지 상세 | 높음 | PK + 캐시 |
| FAQ 목록 (카테고리 + display_order) | 중간 | `idx_faqs_category_order (category, display_order, id) WHERE status='PUBLISHED' AND deleted_at IS NULL` |
| 약관 현재 유효 버전 조회 | 매우 높음 (모든 로그인) | UNIQUE 부분 인덱스 `uk_terms_code_current` 로 O(1) |
| 회원의 미동의 약관 조회 | 매우 높음 (로그인마다) | `idx_term_agreements_member` + `NOT EXISTS` |

### 8.2 캐시

| 캐시 | 키 | TTL | 무효화 |
|---|---|---|---|
| 공지 목록 | `notices:list::{cursor_hash}` | 5분 | 공지 발행/수정/아카이브 시 flush |
| 공지 상세 | `noticeDetail::{id}` | 10분 | 수정 시 evict |
| FAQ 목록 (카테고리) | `faqList::{category}` | 1시간 | 생성/수정/삭제 시 evict |
| FAQ 카테고리 | `faqCategories` | 1시간 | FAQ CRUD 시 evict |
| 약관 현재 유효 | `terms:current` | 1시간 | 약관 발행 시 evict |
| 약관 상세 | `terms::{id}` | 24시간 | (거의 불변) 수정 시 evict |

- 캐시 무효화 — 관리자 CMS 의 변경은 `@CacheEvict` 또는 Redis PUB/SUB 로 broadcast.

### 8.3 대용량 공지 조회

- 활성 공지는 일반적으로 수십~수백 건 수준. Offset 방식도 가능하나 공통 정책상 Cursor 권장.
- 썸네일 이미지는 CloudFront 경유 — 서버 부담 없음.

### 8.4 FAQ 검색

- `ILIKE` 기본. 데이터 수천 건 이하에서는 문제 없음. 그 이상이면 pg_trgm 도입 검토.

---

## 9. 의존성 & 향후 확장

### 9.1 의존 도메인

- **Member(D3)** — `member_term_agreements` FK.
- **Auth(D1)** — 온보딩 완료 시 약관 동의 기록. `POST /api/v1/onboarding/profile/complete` 확장.
- **Notification(D11)** — 공지 발행 시 `ANNOUNCEMENT` broadcast.
- **Upload(공통)** — 공지 썸네일 Presigned 업로드.

### 9.2 확장 포인트

| 확장 | Phase | 비고 |
|---|:---:|---|
| 공지 타겟팅 (지역/Pet Species) | Phase 4+ | `notices.audience_json` JSONB 추가 |
| 공지/FAQ 마크다운 렌더링 | Phase 4+ | `content_format` 컬럼 추가 |
| FAQ 좋아요 / 도움됨 집계 | Phase 5+ | `faq_feedback` 테이블 |
| 약관 다국어 | Phase 5+ | `terms_i18n (term_id, locale, title, content)` |
| 약관 diff view (변경 사항 하이라이트) | Phase 4+ | 버전 간 비교 |
| 공지 푸시 A/B 테스트 | Phase 5+ | variant 필드 |
| 팝업 공지 (앱 진입 시 1회 노출) | Phase 3+ | `notices.popup_until` 컬럼 추가 |
| 약관 전자 서명 / 법적 증적 | Phase 5+ | 해시 저장 + 타임스탬프 |

### 9.3 CLAUDE.md 온보딩 플로우와의 정합성

기존 `CLAUDE.md` 의 온보딩 플로우:
```
POST /api/v1/onboarding/profile/complete → 닉네임 + 약관 동의
```

**정합성 유지 작업**:
1. 기존 요청 바디의 `termsOfServiceAgreed` / `privacyPolicyAgreed` / `marketingConsent` 플래그는 **레거시 유지**.
2. 신규 클라이언트는 `termAgreements` 배열 형태로 제출 — 서버가 두 형식 모두 허용 (하위 호환).
3. 서버는 우선 `termAgreements` 배열 → 없으면 플래그 → 그 결과를 `member_term_agreements` 에 INSERT + `members.*_agreed` 플래그도 업데이트.
4. Phase 4+ 에서 레거시 플래그 제거 + `termAgreements` 만 사용.

### 9.4 ❓ 확인 필요 항목

1. **공지 카테고리 4종(`GENERAL/UPDATE/EVENT/URGENT`) 적정성** — Figma 에 카테고리 탭이 있는지 재확인 필요.
2. **FAQ 카테고리 8종 최종 확정** — 결제 / 유치원 / 커뮤니티 등 추가 여부.
3. **공지 본문 HTML 허용 여부** — plain + `\n` 제안. 링크/이미지 inline 필요성.
4. **약관 재동의 pending 상태에서 쓰기 API 차단 정책** — 모두 차단 vs 특정만 차단.
5. **예약 발행 스케줄러** — Spring `@Scheduled` vs 별도 워커. MVP 는 `@Scheduled(fixedRate=60s)` 제안.
6. **`send_push=TRUE` 기본값** — 공지 발행 시 디폴트 체크 vs 관리자 명시 선택.
7. **약관 버전 문자열 컨벤션** — semver(`1.0.2`) vs 날짜(`2026-04-18`). 권장: **semver + 발효일 보조**.
8. **이미 `notices` 기존 구현이 있으므로 확장 컬럼 마이그레이션 전략** — `ADD COLUMN ... DEFAULT ...` 로 기존 row 영향 최소화.
9. **기존 Member 약관 플래그와 `member_term_agreements` 의 전이 전략** — 마이그레이션 시 현재 ACTIVE 회원의 플래그를 **현재 유효 약관의 동의 이력**으로 백필 필요.

---

## 10. 관련 문서

- [00. 서비스 개요](./00-overview.md)
- [01. 도메인 맵](./01-domain-map.md)
- [02. 용어집](./02-glossary.md)
- [03. 공통 정책](./03-common-policies.md)
- [09. 알림(Notification) 기능 명세](./09-notification-spec.md) — `ANNOUNCEMENT` 이벤트 발행 대상
- [11. 1:1 문의 명세](./11-inquiry-spec.md) — 고객지원 연관 FAQ
- [CLAUDE.md](../../.claude/CLAUDE.md) — 기존 `notices` 구현 + 온보딩 약관 동의 플로우
