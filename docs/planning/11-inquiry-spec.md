# 11. 1:1 문의(Inquiry) 기능 명세

> 본 문서는 로카펫의 **D12. 고객지원/문의(Support)** 도메인 중 **1:1 문의** 영역 상세 기획이다.
> 신고(Report)는 리뷰 · 업체 도메인에 이미 정의되어 있으며([06-review-spec.md §3.7](./06-review-spec.md), [03-common-policies.md §3.2](./03-common-policies.md)), 본 문서 범위 밖이다.
> 상위 문서: [01. 도메인 맵](./01-domain-map.md), [02. 용어집](./02-glossary.md), [03. 공통 정책](./03-common-policies.md)

---

## 1. 개요

### 1.1 도메인 책임

- 회원이 운영진에게 보내는 **개인 1:1 문의**의 작성 · 수정 · 삭제 · 조회.
- 관리자가 admin-api 를 통해 답변을 등록하면, 회원에게 **푸시/인앱 알림**으로 전달.
- 문의 유형별 **카테고리 분류**와 **첨부 이미지**(최대 5장) 지원.
- 상태 머신 `PENDING → ANSWERED → CLOSED` 로 진행.

### 1.2 다른 도메인과의 관계

| 방향 | 대상 도메인 | 관계 |
|---|---|---|
| 참조 | Member(D3) | `inquiries.member_id` → FK (작성자) |
| 참조 | Member(D3) — admin | `inquiry_answers.admin_id` → FK (답변자) |
| 생산 (이벤트) | Notification(D11) | `INQUIRY_ANSWERED` 알림 발송 |
| 독립 | Place/Review | 카테고리로 분류만 — 직접 FK 없음 (Phase 4+ 에서 `related_review_id` 선택 확장) |

### 1.3 Figma 확인 (`479:27291` 작성 화면)

- 상단: **문의유형 드롭다운** ("문의유형을 선택해주세요")
- 제목 입력 (1줄)
- 본문 텍스트 영역, **우측 하단 카운터 `0 / 1,000`** — 본문 최대 **1000자**
- 하단 안내문구:
  - "답변이 오면 앱 PUSH 알림을 드려요."
  - "앱 알림 설정이 꺼져있으면 답변이 와도 알림을 받을 수 없어요."
- 하단 버튼: "제출하기" (비활성 상태 → 유효 시 활성화)

→ **본문 최대 1000자** 로 확정 (Review 도메인의 2000자와 다름).

---

## 2. 엔티티 & 데이터 모델

### 2.1 `inquiries` (문의 본체)

| 컬럼 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `id` | BIGSERIAL | O | - | PK |
| `member_id` | BIGINT | O | - | FK → members.id (작성자) |
| `category` | VARCHAR(30) | O | - | Enum (§2.5) |
| `title` | VARCHAR(50) | O | - | 제목 (1~50자) |
| `content` | VARCHAR(1000) | O | - | 본문 (1~1000자) |
| `status` | VARCHAR(20) | O | `PENDING` | `PENDING` / `ANSWERED` / `CLOSED` |
| `created_at` | TIMESTAMPTZ | O | NOW() | |
| `updated_at` | TIMESTAMPTZ | O | NOW() | |
| `answered_at` | TIMESTAMPTZ | X | NULL | 답변 등록 시각 (집계 편의용 캐시) |
| `closed_at` | TIMESTAMPTZ | X | NULL | 사용자가 닫은 시각 |
| `deleted_at` | TIMESTAMPTZ | X | NULL | 소프트 삭제 (작성자 삭제 or 탈퇴 완료) |

### 2.2 `inquiry_images`

| 컬럼 | 타입 | NOT NULL | 설명 |
|---|---|:---:|---|
| `id` | BIGSERIAL | O | PK |
| `inquiry_id` | BIGINT | O | FK |
| `image_url` | VARCHAR(500) | O | S3/CDN URL |
| `display_order` | INT | O | 0~4 |
| `created_at` | TIMESTAMPTZ | O | |

- UNIQUE (`inquiry_id`, `display_order`).
- 최대 5장 (공통 정책 §5.2 의 리뷰 10장과 다름 — 문의는 가볍게 5장 권장).

### 2.3 `inquiry_answers` (답변 — 1:1)

| 컬럼 | 타입 | NOT NULL | 설명 |
|---|---|:---:|---|
| `id` | BIGSERIAL | O | PK |
| `inquiry_id` | BIGINT | O | **UNIQUE**, FK → inquiries.id (1:1) |
| `admin_id` | BIGINT | O | FK → members.id (role=ADMIN) |
| `content` | TEXT | O | 답변 본문 (1~5000자). plain text + 줄바꿈 |
| `created_at` | TIMESTAMPTZ | O | NOW() |
| `updated_at` | TIMESTAMPTZ | O | NOW() |

- **1 문의 = 1 답변** UNIQUE. 답변 수정은 같은 row 의 `updated_at` 갱신.
- 답변 이력(diff)은 MVP 미지원. 필요 시 `inquiry_answer_histories` 테이블 Phase 4+.

### 2.4 제약 조건 & 인덱스

| 대상 | 표현 | 목적 |
|---|---|---|
| `inquiries` | CHECK `char_length(title) BETWEEN 1 AND 50` | |
| `inquiries` | CHECK `char_length(content) BETWEEN 1 AND 1000` | |
| `inquiries` | `idx_inquiries_member_created (member_id, created_at DESC) WHERE deleted_at IS NULL` | 내 문의 목록 |
| `inquiries` | `idx_inquiries_status_created (status, created_at DESC) WHERE deleted_at IS NULL` | admin 대시보드 (PENDING 우선) |
| `inquiry_images` | PK `(id)`, UNIQUE `(inquiry_id, display_order)` | 정렬 |
| `inquiry_answers` | UNIQUE `(inquiry_id)` | 1:1 |
| `inquiry_answers` | `idx_inquiry_answers_admin (admin_id, created_at DESC)` | 관리자별 답변 이력 |

### 2.5 Enum — 문의 카테고리

```kotlin
enum class InquiryCategory {
    SERVICE_ERROR,          // 서비스 오류 (앱 버그, 크래시, 데이터 오류)
    PLACE_INFO_ERROR,       // 업체 정보 오류 (주소/영업시간/동반정책)
    ACCOUNT_ISSUE,          // 계정 문제 (로그인/탈퇴/본인인증)
    REVIEW_REPORT_REQUEST,  // 리뷰 관련 요청 (수정/삭제 요청 등)
    FEATURE_REQUEST,        // 기능 제안
    ETC                     // 기타
}
```

- Figma 드롭다운 ("문의유형을 선택해주세요") 에 해당. 라벨은 클라이언트가 Enum 매핑.

### 2.6 Enum — 상태

```kotlin
enum class InquiryStatus { PENDING, ANSWERED, CLOSED }
```

### 2.7 상태 머신

```
                 answer
   ┌─────────┐          ┌───────────┐     user close
   │ PENDING │─────────▶│ ANSWERED  │─────────────────▶ ┌────────┐
   └─────────┘          └─────┬─────┘                   │ CLOSED │
        │                     │  admin revise           └────────┘
        │                     │  (content 수정,
        │                     │   상태는 ANSWERED 유지)
        │                     └──────┐
        │                            ▼
        │                     (self-loop)
        │
        │  user delete (PENDING 에서만)
        ▼
   ┌──────────┐
   │ DELETED  │ (deleted_at)
   └──────────┘
```

- **`PENDING` 상태에서만 사용자가 수정/삭제 가능**. `ANSWERED`/`CLOSED` 에서는 읽기 전용.
- `CLOSED` 는 사용자가 직접 닫은 상태. 더 이상 재오픈 불가 (Phase 3+ 재오픈 지원 검토).
- `ANSWERED` → `CLOSED` 자동 전환은 **MVP 미지원** — 유저의 명시적 닫기만.
- 관리자 답변 수정은 상태 전이 없음 (`updated_at` 만 갱신).

---

## 3. 주요 기능 (Use Case)

### 3.1 문의 작성

- **대상 화면**:
  [문의 작성](node-id=479:27291),
  [유형 선택 모달](node-id=479:27312),
  [작성 완료](node-id=479:27334),
  [카테고리 수정 변형](node-id=479:27377),
  [작성 변형](node-id=522:28949)

- **엔드포인트**: `POST /api/v1/inquiries`
- **사전조건**:
  - 로그인 + `onboardingStage = COMPLETED`.
- **입력**:
  ```json
  {
    "category": "SERVICE_ERROR",
    "title": "앱이 자꾸 꺼져요",
    "content": "로그인 후 홈 화면에서 2~3초 뒤에 앱이 종료됩니다...",
    "imageIds": ["img_01HX...", "img_01HY..."]
  }
  ```
- **동작**:
  1. 입력 검증 (category Enum, title/content 길이, imageIds ≤ 5, 모두 COMMIT 된 업로드).
  2. `inquiries` INSERT (`status = PENDING`).
  3. `inquiry_images` INSERT (순서대로 `display_order = 0..n-1`).
- **응답**: `InquiryResponse` (§5.3).
- **권한**: accessToken 필수.

### 3.2 내 문의 목록 조회

- **대상 화면**:
  [문의 목록](node-id=522:28838),
  [목록 스크롤](node-id=565:41948), [565:42034](node-id=565:42034), [565:42120](node-id=565:42120)

- **엔드포인트**: `GET /api/v1/inquiries?cursor=...&size=20&status=ALL`
- **동작**:
  - `member_id = :me AND deleted_at IS NULL`.
  - `status` 파라미터 `ALL`(기본) / `PENDING` / `ANSWERED` / `CLOSED`.
  - 정렬: `created_at DESC, id DESC`.
- **응답 아이템**:
  ```json
  {
    "id": 22,
    "category": "SERVICE_ERROR",
    "title": "앱이 자꾸 꺼져요",
    "status": "ANSWERED",
    "hasAnswer": true,
    "createdAt": "2026-04-15T09:00:00Z",
    "answeredAt": "2026-04-16T10:00:00Z"
  }
  ```
- **권한**: accessToken 필수. 본인 것만.

### 3.3 문의 상세 조회

- **엔드포인트**: `GET /api/v1/inquiries/{id}`
- **응답**: 본체 + 이미지 배열 + 답변(있으면).
- **규칙**: 본인 소유만 (`INQUIRY_004` 403).
- **권한**: accessToken 필수.

### 3.4 문의 수정 — 답변 전에만

- **대상 화면**:
  [답변 미완료 수정](node-id=522:28723),
  [수정 변형](node-id=479:27179)

- **엔드포인트**: `PATCH /api/v1/inquiries/{id}`
- **입력**: `category` / `title` / `content` / `imageIds` 부분 업데이트.
- **규칙**:
  - `status = PENDING` 인 경우에만 허용. 그 외는 `INQUIRY_003` (409).
  - `imageIds` 교체 시 기존 이미지는 soft delete, 신규 INSERT.
- **권한**: 본인 소유 + `PENDING` 상태.

### 3.5 문의 삭제 — 답변 전에만

- **엔드포인트**: `DELETE /api/v1/inquiries/{id}`
- **규칙**:
  - `status = PENDING` 만 허용. 그 외 `INQUIRY_003`.
  - 소프트 삭제 (`deleted_at = NOW()`).
- **권한**: 본인 소유.

### 3.6 문의 닫기 (Close)

- **엔드포인트**: `POST /api/v1/inquiries/{id}/close`
- **사전조건**: `status = ANSWERED` (답변 받은 후에만 닫을 수 있음).
- **동작**: `status = CLOSED`, `closed_at = NOW()`. 더 이상 상태 전이 없음.
- **에러**: `PENDING` 에서 닫기 시도 → `INQUIRY_005` (409).
- **권한**: 본인 소유.

### 3.7 답변 등록 — 관리자

- **엔드포인트**: `POST /admin/api/v1/inquiries/{id}/answer`
- **입력**:
  ```json
  {
    "content": "안녕하세요, 로카펫입니다...\n해당 이슈는..."
  }
  ```
- **동작**:
  1. `inquiries` 존재 + `status = PENDING` 검증. 이미 답변이면 409 or PUT 으로 덮어쓰기.
  2. `inquiry_answers` INSERT (UNIQUE `inquiry_id`).
  3. `inquiries.status = ANSWERED`, `answered_at = NOW()`.
  4. `InquiryAnsweredEvent` 발행 → Notification 도메인이 `INQUIRY_ANSWERED` 알림 생성 ([09-notification-spec.md §3.7](./09-notification-spec.md)).
- **권한**: admin-api + `role = ADMIN`.

### 3.8 답변 수정 — 관리자

- **엔드포인트**: `PATCH /admin/api/v1/inquiries/{id}/answer`
- **동작**: 기존 `inquiry_answers.content` 갱신 + `updated_at = NOW()`. **상태 전이 없음**.
- **알림 재발송 여부**: **발송 안 함** (스팸 방지). 중요한 수정은 신규 공지/DM 으로 처리 권장.
- **권한**: admin-api + ADMIN.

### 3.9 답변 삭제 — 관리자 (예외 플로우)

- **엔드포인트**: `DELETE /admin/api/v1/inquiries/{id}/answer`
- **동작**:
  - `inquiry_answers` soft delete 또는 hard delete (❓ 정책 결정). MVP 제안: **hard delete**.
  - `inquiries.status = PENDING` 으로 되돌림 + `answered_at = NULL`.
- **사용 케이스**: 오답변을 완전히 취소할 때. 매우 드문 케이스.
- **권한**: admin-api + ADMIN.

### 3.10 관리자 문의 목록

- **엔드포인트**: `GET /admin/api/v1/inquiries?status=PENDING&category=...&cursor=...`
- **필터**: 상태, 카테고리, 기간, 작성자 ID.
- **정렬**: `status='PENDING'` 우선 + 오래된 순 (SLA 관점).
- **권한**: admin-api + ADMIN.

---

## 4. 비즈니스 규칙 & 제약

### 4.1 도메인 불변식

1. **작성자는 `PENDING` 상태에서만 수정/삭제 가능**. `ANSWERED`/`CLOSED` 는 읽기 전용.
2. **한 문의에 답변은 1개**. UNIQUE `(inquiry_id)` 물리 보장.
3. **답변 등록 시 상태는 자동 `ANSWERED`**.
4. **`CLOSED` 는 `ANSWERED` 에서만 전이 가능**. 답변 없는데 닫기 불가.
5. **이미지 최대 5장**.
6. **내 문의만 조회 가능** — 타인 문의 API 노출 없음.
7. **답변 등록 시 알림 발송**, 수정 시 미발송.
8. **탈퇴 완료 시 문의 처리** — `inquiries` 는 **유지** (운영 감사 목적), `member_id` 는 익명화 참조. [03-common-policies.md §2.1](./03-common-policies.md) 참조.

### 4.2 입력 제약

| 필드 | 제약 |
|---|---|
| `category` | Enum 값 (§2.5) |
| `title` | 1~50자, trim 후 |
| `content` | 1~1000자, trim 후, 연속 공백 정리 |
| `imageIds` | 0~5개, COMMIT 완료된 업로드 |
| 답변 `content` | 1~5000자 |

### 4.3 콘텐츠 정책

- **본문 포맷**: plain text + `\n` 줄바꿈 보존. 마크다운 미지원.
- **링크**: 자동 감지 클라이언트 책임. 서버 측 렌더링 없음.
- **개인정보 패턴**: 전화번호·주민번호 패턴 경고 (공통 정책 §3.1). MVP 는 경고만, 저장은 허용.
- **첨부 이미지**: Presigned 업로드 ([03-common-policies.md §5](./03-common-policies.md)). EXIF 제거 권장.

### 4.4 상태 전이 규칙

| 현재 → 다음 | 트리거 | 권한 |
|---|---|---|
| `(none) → PENDING` | 작성 | 회원 본인 |
| `PENDING → ANSWERED` | 답변 등록 | 관리자 |
| `ANSWERED → ANSWERED` | 답변 수정 (self-loop) | 관리자 |
| `ANSWERED → PENDING` | 답변 삭제 (예외) | 관리자 |
| `ANSWERED → CLOSED` | 닫기 | 회원 본인 |
| `PENDING → DELETED` | 삭제 | 회원 본인 |
| `ANSWERED/CLOSED → DELETED` | 삭제 시도 | **불가** |

---

## 5. API 엔드포인트 초안 (REST)

### 5.1 app-api (회원용)

| Method | Path | Auth | 설명 |
|---|---|:---:|---|
| POST | `/api/v1/inquiries` | accessToken | 문의 작성 |
| GET | `/api/v1/inquiries` | accessToken | 내 문의 목록 |
| GET | `/api/v1/inquiries/{id}` | accessToken | 상세 (답변 포함) |
| PATCH | `/api/v1/inquiries/{id}` | accessToken | 수정 (PENDING 에서만) |
| DELETE | `/api/v1/inquiries/{id}` | accessToken | 삭제 (PENDING 에서만) |
| POST | `/api/v1/inquiries/{id}/close` | accessToken | 닫기 (ANSWERED → CLOSED) |

### 5.2 admin-api (관리자용)

| Method | Path | 설명 |
|---|---|---|
| GET | `/admin/api/v1/inquiries` | 관리자 목록 (필터/정렬) |
| GET | `/admin/api/v1/inquiries/{id}` | 관리자 상세 |
| POST | `/admin/api/v1/inquiries/{id}/answer` | 답변 등록 |
| PATCH | `/admin/api/v1/inquiries/{id}/answer` | 답변 수정 |
| DELETE | `/admin/api/v1/inquiries/{id}/answer` | 답변 삭제 (예외) |

### 5.3 응답 스키마

#### 문의 상세

```json
{
  "id": 22,
  "category": "SERVICE_ERROR",
  "categoryLabel": "서비스 오류",
  "title": "앱이 자꾸 꺼져요",
  "content": "로그인 후 홈 화면에서 2~3초 뒤에 앱이 종료됩니다...",
  "images": [
    { "id": 301, "imageUrl": "https://cdn.locapet.app/inquiries/22/img1.jpg", "displayOrder": 0 }
  ],
  "status": "ANSWERED",
  "answer": {
    "content": "안녕하세요, 로카펫입니다...\n해당 이슈는 v1.2.1 에서 수정되었어요.",
    "createdAt": "2026-04-16T10:00:00Z",
    "updatedAt": "2026-04-16T10:00:00Z"
  },
  "createdAt": "2026-04-15T09:00:00Z",
  "updatedAt": "2026-04-15T09:00:00Z",
  "answeredAt": "2026-04-16T10:00:00Z",
  "closedAt": null
}
```

#### 문의 목록

```json
{
  "items": [
    {
      "id": 22,
      "category": "SERVICE_ERROR",
      "title": "앱이 자꾸 꺼져요",
      "status": "ANSWERED",
      "hasAnswer": true,
      "createdAt": "2026-04-15T09:00:00Z",
      "answeredAt": "2026-04-16T10:00:00Z"
    }
  ],
  "nextCursor": "eyJpZCI6MjF9",
  "hasNext": true
}
```

---

## 6. 에러 코드

`INQUIRY_{NNN}`.

| 코드 | HTTP | 메시지 | 발생 상황 |
|---|:---:|---|---|
| `INQUIRY_001` | 404 | 문의를 찾을 수 없어요. | 없는 id, 삭제됨 |
| `INQUIRY_002` | 400 | 문의 입력값을 확인해주세요. | category Enum, 길이, 이미지 수 |
| `INQUIRY_003` | 409 | 답변이 등록된 문의는 수정·삭제할 수 없어요. | `ANSWERED`/`CLOSED` 에서 수정/삭제 시도 |
| `INQUIRY_004` | 403 | 본인의 문의만 조회·수정할 수 있어요. | 타인 문의 접근 |
| `INQUIRY_005` | 409 | 답변이 등록된 문의만 닫을 수 있어요. | `PENDING` 에서 close 시도 |
| `INQUIRY_006` | 409 | 이미 답변이 등록된 문의예요. | 중복 답변 등록 시 (admin) |
| `INQUIRY_007` | 400 | 첨부 이미지는 최대 5장까지 가능해요. | 초과 |
| `UPLOAD_002` | 400 | 업로드가 완료되지 않은 이미지예요. | COMMIT 안 된 imageId |

---

## 7. 엣지 케이스 & 예외 처리

| 케이스 | 처리 |
|---|---|
| **답변 등록 직전 사용자가 수정/삭제** | 동시성 — 트랜잭션 내에서 `SELECT ... FOR UPDATE` + 상태 체크. 사용자가 먼저면 관리자 요청 실패. |
| **관리자가 답변 후 사용자가 수정 시도** | 409 `INQUIRY_003`. UI 에서는 버튼 비활성화로 사전 방어. |
| **첨부 이미지 5장 초과** | 400 `INQUIRY_007`. 클라이언트가 사전 차단해도 서버 방어. |
| **`PENDING` 상태에서 `close` 호출** | 409 `INQUIRY_005`. |
| **`CLOSED` 상태에서 재오픈 시도** | MVP 는 미지원. 재오픈 하려면 새 문의 작성. |
| **탈퇴 유예 중 문의 조회/작성** | 허용 (ACTIVE). 탈퇴 완료 시 `deleted_at` 세팅 또는 `member_id` 익명화 (공통 정책 §2.1). |
| **탈퇴 후 남은 문의에 관리자가 답변** | 가능 — 답변은 등록되나 알림 발송은 skip (수신자 비활성). 인앱 저장만. |
| **같은 회원이 동일 내용 연속 제출** | 중복 방지 강제는 없음. 어뷰징 의심 시 운영이 수동 차단. Rate limit 권장 (분당 3건 등). |
| **본문에 전화번호/이메일 등 개인정보** | 패턴 매칭 경고 (선택). 저장은 허용. 필요 시 관리자가 마스킹 후 처리. |
| **이미지 업로드 미완료로 제출** | `UPLOAD_002` 400. 클라이언트가 업로드 완료 후 `imageIds` 전달. |
| **관리자가 답변 수정 시 알림 재발송 필요** | MVP 는 재발송 안 함. 필요 시 별도 "새 안내 드림" 답변 추가 권장. |
| **동시성 — 답변 등록 중 UNIQUE 충돌** | UNIQUE `(inquiry_id)` 위반 시 `INQUIRY_006`. |
| **알림 발송 실패로 답변만 저장됨** | 답변 등록은 트랜잭션 커밋, 알림 발송은 after-commit 훅(outbox). 푸시 실패는 인앱 알림으로 fallback. |

### 7.1 동시성 상세

```
Timeline:
  T1 (user)      T2 (admin)
  ─────────     ───────────
  PATCH 요청
  status=PENDING 확인
                 POST /answer 요청
                 status=PENDING 확인
                 INSERT inquiry_answers
                 status = ANSWERED (COMMIT)
  UPDATE content (SELECT ... FOR UPDATE 없이)
  → stale write — 답변 등록 후 수정?
```

→ **방어**: PATCH 도 트랜잭션 내 `SELECT status FROM inquiries WHERE id=? FOR UPDATE` 로 최신 상태 확인 후 `ANSWERED` 면 409 반환.

---

## 8. 성능 / 인덱싱 고려사항

### 8.1 주요 쿼리 패턴

| 패턴 | 빈도 | 인덱스 |
|---|:---:|---|
| 내 문의 목록 | 중간 | `idx_inquiries_member_created` |
| 문의 상세 | 중간 | PK + 이미지/답변 2회 추가 쿼리 또는 fetch join |
| admin PENDING 목록 | 중간 (관리자) | `idx_inquiries_status_created` |
| 답변 단건 조회 | 중간 | UNIQUE `(inquiry_id)` |

### 8.2 캐시

- **인쿼리 데이터는 캐시하지 않음** — 개인화 + 쓰기 빈도가 낮음.
- 관리자 대시보드 통계(`PENDING` 건수)는 Redis TTL 1분 캐시 고려 (Phase 4+).

### 8.3 쿼리 최적화

- 문의 상세는 단일 쿼리로:
  ```sql
  SELECT i.*, a.*, img.*
    FROM inquiries i
    LEFT JOIN inquiry_answers a ON a.inquiry_id = i.id
    LEFT JOIN inquiry_images img ON img.inquiry_id = i.id
   WHERE i.id = :id AND i.deleted_at IS NULL;
  ```
  또는 JPA `@EntityGraph` 로 N+1 방지.

### 8.4 스팸 / Rate Limit

- 회원당 **분당 3건, 일 30건** 문의 작성 제한 (❓ 임계값 확인). Redis counter 기반.
- 초과 시 `INQUIRY_008` (429) 제안.

### 8.5 첨부 이미지 저장소

- S3 구조: `s3://locapet-media/inquiries/{inquiryId}/{imageId}.{ext}` ([03-common-policies.md §5.3](./03-common-policies.md)).
- 삭제된 문의의 이미지는 배치로 정리.

---

## 9. 의존성 & 향후 확장

### 9.1 의존 도메인

- **Member(D3)** — FK.
- **Notification(D11)** — `INQUIRY_ANSWERED` 이벤트 발행.
- **Upload(공통)** — Presigned 이미지 업로드.
- **admin-api** — 답변 관리.

### 9.2 확장 포인트

| 확장 | Phase | 비고 |
|---|:---:|---|
| 답변 이력 (버전 관리) | Phase 4+ | `inquiry_answer_histories` |
| 재오픈 / 추가 질의 | Phase 3+ | `CLOSED → PENDING` 전이 + 스레드 구조 |
| 연관 리뷰/업체 링크 | Phase 3+ | `related_review_id`, `related_place_id` nullable FK |
| 내부 메모 (관리자 끼리만) | Phase 4+ | `inquiry_internal_notes` |
| SLA / 평균 응답 시간 통계 | Phase 4+ | admin 대시보드 |
| 유저 만족도 평가 (답변 도움됨) | Phase 4+ | `inquiry_satisfaction` |
| AI 기반 자동 답변 제안 | Phase 5+ | |
| 카테고리 세분화 / 트리 구조 | Phase 4+ | `inquiry_categories` 마스터 테이블 |
| 이메일 fallback (푸시 안 받는 유저) | Phase 3+ | Member 이메일 연계 |
| 공개 Q&A (FAQ 로 승격) | Phase 4+ | 문의 → FAQ 전환 액션 |

### 9.3 Notification 연동 상세

```kotlin
// inquiry-api (app-api 또는 admin-api)
@TransactionalEventListener(phase = AFTER_COMMIT)
fun onInquiryAnswered(event: InquiryAnsweredEvent) {
    notificationService.send(
        memberId = event.inquiryMemberId,
        type = NotificationType.INQUIRY_ANSWERED,
        title = "문의에 답변이 도착했어요",
        body = "[${event.categoryLabel}] ${event.inquiryTitle}",
        payload = mapOf(
            "deepLink" to "locapet://inquiries/${event.inquiryId}",
            "inquiryId" to event.inquiryId.toString()
        )
    )
}
```

- **AFTER_COMMIT** — 답변 등록 트랜잭션 커밋 후에만 알림 발행. 롤백 시 알림 나가지 않도록.
- 인앱 알림은 설정과 무관하게 저장. 푸시는 설정 존중.

### 9.4 ❓ 확인 필요 항목

1. **문의 카테고리 6종 최종 확정** — Figma 드롭다운 옵션 재확인 필요.
2. **본문 길이 1000자 적정성** — Figma 명시값. 리뷰 2000자와의 차이 의도적인지.
3. **답변 수정 시 알림 재발송 여부** — MVP 는 안 함. 중대한 변경은 신규 공지로.
4. **`ANSWERED → CLOSED` 자동 전이 정책** — 7일 후 자동 닫기? MVP 는 수동.
5. **재오픈 / 추가 질의 스레드 구조** — Phase 3+ 에서 도입 시 스키마 확장.
6. **Rate limit 수치** — 분당 3건, 일 30건 제안 → 운영 후 조정.
7. **답변 삭제(hard delete) vs soft delete** — MVP hard delete 제안. 감사 로그 필요하면 soft + history 테이블.
8. **개인정보 패턴 자동 마스킹** — MVP 는 경고만. 자동 마스킹은 Phase 4+.
9. **탈퇴 완료 후 문의 처리** — 유지 vs 삭제. 공통 정책 §2.1 에서 "유지 + 익명화" 제안 → 확정.
10. **`related_review_id` / `related_place_id` 도입 시점** — `REVIEW_REPORT_REQUEST`, `PLACE_INFO_ERROR` 카테고리는 연결 대상 ID 가 있는 편이 자연스러움.

---

## 10. 관련 문서

- [00. 서비스 개요](./00-overview.md)
- [01. 도메인 맵](./01-domain-map.md)
- [02. 용어집](./02-glossary.md)
- [03. 공통 정책](./03-common-policies.md)
- [06. 리뷰(Review) 기능 명세](./06-review-spec.md) — 신고 플로우
- [09. 알림(Notification) 기능 명세](./09-notification-spec.md) — `INQUIRY_ANSWERED` 이벤트 수신
- [10. 공지/FAQ/약관 명세](./10-announcement-spec.md) — FAQ 연동 (문의 전에 FAQ 유도)
