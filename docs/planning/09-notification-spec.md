# 09. 알림(Notification) 기능 명세

> 본 문서는 로카펫의 **D11. 알림/공지(Notification)** 도메인 중 **알림(인앱 + 푸시)** 영역 상세 기획이다.
> 공지사항 · FAQ · 약관은 [10-announcement-spec.md](./10-announcement-spec.md) 에서 분리하여 다룬다.
> 상위 문서: [01. 도메인 맵](./01-domain-map.md), [02. 용어집](./02-glossary.md), [03. 공통 정책](./03-common-policies.md)

---

## 1. 개요

### 1.1 도메인 책임

- **인앱 알림** — 회원별 알림 목록(앱 내 알림센터)을 제공한다. 읽음/안읽음 관리, 정리 정책 포함.
- **푸시 알림** — FCM(Android) · APNs(iOS) 를 통한 푸시 발송.
- **알림 설정** — 타입별 푸시 on/off, 전체 마스터 스위치, 야간 방해금지(선택).
- **디바이스 토큰 관리** — 다중 디바이스 지원, 무효 토큰 자동 비활성화.
- **점검/공지 구별** — `maintenances` 는 별도 앱 블로킹 UI(스플래시 메타)이며 알림 시스템과 **분리**. 일반 공지(Announcement)는 알림 타입으로 발행 가능.

### 1.2 다른 도메인과의 관계

| 방향 | 대상 도메인 | 관계 |
|---|---|---|
| 참조 | Member(D3) | `notifications.member_id`, `device_tokens.member_id`, `notification_settings.member_id` → FK |
| 소비 (이벤트) | Review(D9) | `REVIEW_LIKED` — 내 리뷰에 좋아요 이벤트 |
| 소비 (이벤트) | Announcement(D2) | `ANNOUNCEMENT` — 새 공지 발행 이벤트 |
| 소비 (이벤트) | Inquiry(D12) | `INQUIRY_ANSWERED` — 1:1 문의 답변 등록 이벤트 |
| 독립 | Maintenance(D2) | 점검 공지는 스플래시 메타 경로 — 알림과 분리 |

### 1.3 용어 정리

- **인앱 알림** — DB 저장된 `notifications` row. 앱 내 알림센터 목록.
- **푸시 알림** — FCM/APNs 로 OS 레벨 푸시 전송. 인앱 알림과 쌍으로 존재하는 것이 기본이나, 푸시만 or 인앱만도 가능.
- **이벤트** — 다른 도메인에서 발행하는 도메인 이벤트(예: `ReviewLikedEvent`). Notification 도메인의 Listener 가 수신 → 인앱 레코드 생성 + 푸시 발송.

---

## 2. 엔티티 & 데이터 모델

### 2.1 `notifications` (인앱 알림)

| 컬럼 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `id` | BIGSERIAL | O | - | PK |
| `member_id` | BIGINT | O | - | FK → members.id (수신자) |
| `type` | VARCHAR(30) | O | - | Enum (§2.5) |
| `title` | VARCHAR(100) | O | - | 알림 제목 |
| `body` | VARCHAR(300) | O | - | 알림 본문 |
| `payload_json` | JSONB | X | NULL | 딥링크/추가 데이터 (§2.6) |
| `is_read` | BOOLEAN | O | FALSE | 읽음 여부 |
| `read_at` | TIMESTAMPTZ | X | NULL | 읽은 시각 |
| `created_at` | TIMESTAMPTZ | O | NOW() | |
| `deleted_at` | TIMESTAMPTZ | X | NULL | 소프트 삭제 (유저 삭제 or 90일 정리) |

### 2.2 `device_tokens` (푸시 토큰)

| 컬럼 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `id` | BIGSERIAL | O | - | PK |
| `member_id` | BIGINT | O | - | FK → members.id |
| `platform` | VARCHAR(10) | O | - | `IOS` / `ANDROID` |
| `token` | VARCHAR(500) | O | - | FCM token or APNs device token |
| `app_version` | VARCHAR(20) | X | NULL | 등록 시점 앱 버전 |
| `locale` | VARCHAR(10) | X | NULL | `ko-KR`, `en-US` 등 |
| `last_active_at` | TIMESTAMPTZ | O | NOW() | 마지막 ping / 로그인 / 토큰 재등록 시각 |
| `is_active` | BOOLEAN | O | TRUE | 무효(InvalidRegistration) 감지 시 FALSE |
| `created_at` | TIMESTAMPTZ | O | NOW() | |

- **UNIQUE** `(token)` — 같은 물리 토큰이 다른 회원으로 옮겨갈 경우 UPSERT: 기존 row 의 `member_id` 를 새 회원으로 이관하고 `is_active=TRUE` 재활성.

### 2.3 `notification_settings` (회원별 설정)

| 컬럼 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `member_id` | BIGINT | O | - | **PK** + FK → members.id. 1:1 |
| `push_enabled` | BOOLEAN | O | TRUE | 전체 푸시 마스터 스위치 |
| `types_json` | JSONB | O | `{}` | 타입별 on/off (§2.7) |
| `quiet_hours_start` | TIME | X | NULL | 방해금지 시작 (KST, 예: 22:00) |
| `quiet_hours_end` | TIME | X | NULL | 방해금지 종료 (예: 08:00) — start > end 면 자정 넘김 |
| `created_at` | TIMESTAMPTZ | O | NOW() | |
| `updated_at` | TIMESTAMPTZ | O | NOW() | |

### 2.4 `notification_send_logs` (발송 이력 — 선택)

운영 디버깅/리트라이용. **MVP 범위 밖**. Phase 4+ 에서 도입.

```sql
-- 참고 스키마 (MVP 미포함)
CREATE TABLE notification_send_logs (
    id BIGSERIAL PRIMARY KEY,
    notification_id BIGINT NOT NULL,
    device_token_id BIGINT NOT NULL,
    platform VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,    -- SENT / FAILED / SKIPPED
    error_code VARCHAR(50),
    sent_at TIMESTAMPTZ NOT NULL
);
```

### 2.5 `NotificationType` Enum

```kotlin
enum class NotificationType(val group: Group) {
    // 상호작용
    REVIEW_LIKED(Group.INTERACTION),       // 내 리뷰에 좋아요

    // 공지/시스템
    ANNOUNCEMENT(Group.ANNOUNCEMENT),      // 관리자 공지
    SYSTEM(Group.SYSTEM),                  // 앱 업데이트, 정책 변경 안내

    // 고객지원
    INQUIRY_ANSWERED(Group.SUPPORT);       // 1:1 문의 답변

    enum class Group { INTERACTION, ANNOUNCEMENT, SYSTEM, SUPPORT }
}
```

- **Group** 은 알림 설정 UI 에서 대분류로 사용. 유저는 그룹 단위 또는 개별 타입 단위 on/off 선택 (Phase 3 UX 확정).

### 2.6 `payload_json` 스키마

딥링크 + 화면 이동용 메타.

```jsonc
// REVIEW_LIKED
{
  "deepLink": "locapet://reviews/1001",
  "reviewId": 1001,
  "placeId": 42,
  "likedByMemberId": 7    // 발신자 (선택, 익명 처리 가능)
}

// ANNOUNCEMENT
{
  "deepLink": "locapet://notices/15",
  "noticeId": 15
}

// INQUIRY_ANSWERED
{
  "deepLink": "locapet://inquiries/22",
  "inquiryId": 22
}

// SYSTEM
{
  "deepLink": "locapet://meta/version",
  "actionHint": "FORCE_UPDATE"
}
```

- 딥링크 스킴은 `locapet://` 고정. 미지원 경로일 경우 홈으로 fallback.

### 2.7 `notification_settings.types_json` 스키마

```jsonc
{
  "REVIEW_LIKED": true,
  "ANNOUNCEMENT": true,
  "INQUIRY_ANSWERED": true,
  "SYSTEM": true
}
```

- **누락 키는 기본 `true`** 로 해석 (opt-out 방식).
- `push_enabled=false` 이면 타입별 설정 무관하게 푸시 전부 차단.

### 2.8 제약 조건 & 인덱스

| 대상 | 표현 | 목적 |
|---|---|---|
| `notifications` | `idx_notifications_member_created (member_id, created_at DESC) WHERE deleted_at IS NULL` | 내 알림 목록 |
| `notifications` | `idx_notifications_member_unread (member_id) WHERE is_read = FALSE AND deleted_at IS NULL` | 뱃지 카운트 |
| `device_tokens` | UNIQUE `(token)` | 토큰 유일성 |
| `device_tokens` | `idx_device_tokens_member_active (member_id) WHERE is_active = TRUE` | 발송 시 조회 |
| `notification_settings` | PK `(member_id)` | 1:1 |

### 2.9 상태 머신 — 인앱 알림

```
     create
   ┌───────────┐   read    ┌──────────┐   delete   ┌──────────┐
   │  UNREAD   │──────────▶│   READ   │───────────▶│ DELETED  │ (soft)
   │           │           │          │           │          │
   │is_read=F  │           │is_read=T │           │deleted_at│
   └───────────┘           └──────────┘           └──────────┘
         │                                              ▲
         └──────────────────────────────────────────────┘
                          delete (unread)
```

- 90일 후 **배치 hard delete** ([03-common-policies.md §2.3](./03-common-policies.md) 감사 로그 대상 아님).

---

## 3. 주요 기능 (Use Case)

### 3.1 알림 목록 조회

- **대상 화면**: [알림 목록](node-id=479:26663)

- **엔드포인트**: `GET /api/v1/notifications?cursor=...&size=20&onlyUnread=false`
- **동작**:
  - `deleted_at IS NULL` + `member_id = :me`.
  - `onlyUnread=true` 시 `is_read = FALSE` 필터.
  - 정렬 `created_at DESC, id DESC`.
- **응답 아이템**:
  ```json
  {
    "id": 5001,
    "type": "REVIEW_LIKED",
    "title": "내 리뷰에 좋아요가 눌렸어요",
    "body": "강아지와 식당에 남긴 리뷰에 좋아요가 추가됐어요.",
    "payload": { "deepLink": "locapet://reviews/1001", "reviewId": 1001, "placeId": 42 },
    "isRead": false,
    "createdAt": "2026-04-17T11:00:00Z"
  }
  ```
- **권한**: accessToken 필수.

### 3.2 알림 읽음 처리

- **엔드포인트**:
  - `POST /api/v1/notifications/{id}/read` — 단건 읽음
  - `POST /api/v1/notifications/read-all` — 전체 읽음 (본인 것)
- **동작**: `is_read = TRUE`, `read_at = NOW()`. 이미 읽은 경우 idempotent.
- **엣지**: 클라이언트가 목록 조회 시점에 **자동 읽음 처리 여부** ❓ — MVP 제안: **클라이언트 탭 시점에만 읽음**. 리스트 열기만으로는 미처리.
- **권한**: 본인 알림만.

### 3.3 알림 삭제

- **단건 삭제**: `DELETE /api/v1/notifications/{id}` — 소프트 삭제.
- **전체 삭제**: `DELETE /api/v1/notifications` — 본인 전체 소프트 삭제.
- **권한**: 본인 알림만.

### 3.4 안읽음 카운트

- **엔드포인트**: `GET /api/v1/notifications/unread-count`
- **응답**: `{ "unreadCount": 3 }`
- **사용처**: 앱 홈/마이페이지 뱃지.
- **성능**: `idx_notifications_member_unread` 부분 인덱스 커버. 100ms 이내.
- **캐시**: Redis `notifications:unread:{memberId}` TTL 30초 (선택 — 트래픽 높으면 도입).

### 3.5 알림 설정 조회/변경

- **대상 화면**:
  [알림 설정](node-id=479:26451), [알림 설정 헤더](node-id=479:47142)

- **엔드포인트**:
  - `GET /api/v1/notifications/settings` — 본인 설정 조회
  - `PATCH /api/v1/notifications/settings` — 부분 업데이트

- **입력 예**:
  ```json
  {
    "pushEnabled": true,
    "types": {
      "REVIEW_LIKED": true,
      "ANNOUNCEMENT": false,
      "INQUIRY_ANSWERED": true,
      "SYSTEM": true
    },
    "quietHoursStart": "22:00",
    "quietHoursEnd": "08:00"
  }
  ```
- **규칙**:
  - 최초 요청 시 row 없으면 기본값으로 자동 생성.
  - `quietHoursStart`/`End` 는 KST 기준.
  - `quietHoursStart == quietHoursEnd` 는 "방해금지 없음"으로 해석 (둘 다 NULL 로 정규화).
- **권한**: accessToken 필수.

### 3.6 디바이스 토큰 등록/갱신

- **엔드포인트**:
  - `POST /api/v1/notifications/devices` — 등록/갱신 (UPSERT)
  - `DELETE /api/v1/notifications/devices/{token}` — 로그아웃 시 비활성화 (또는 삭제)

- **입력**:
  ```json
  {
    "platform": "IOS",
    "token": "fcm_or_apns_token_string",
    "appVersion": "1.2.3",
    "locale": "ko-KR"
  }
  ```
- **동작**:
  - `token` UNIQUE 충돌 시 `member_id` 를 현재 회원으로 이관 + `is_active=TRUE` + `last_active_at=NOW()`.
  - 로그인/앱 시작 시마다 갱신 권장 — `last_active_at` 으로 비활성 토큰 정리 근거.
- **로그아웃 시**: 해당 토큰 `is_active=FALSE` 처리. 물리 삭제는 배치.
- **권한**: accessToken 필수.

### 3.7 알림 발송 — 내부 Use Case (이벤트 수신)

외부 API 아님. 도메인 이벤트 → Notification 서비스 로직.

```
1. 이벤트 발행 (예: ReviewLikedEvent{reviewAuthorId, likerId, reviewId})
2. Listener 수신:
   a. 자기 자신에게 오는 알림이면 DROP (REVIEW_LIKED 에서 좋아요 누른 사람 ≠ 리뷰 작성자 검증)
   b. notification_settings 조회 → 타입 on/off, push_enabled, quiet_hours 확인
   c. notifications INSERT (인앱 알림은 항상 저장 — 설정과 무관. 푸시만 설정 영향)
   d. 푸시 발송 조건 만족 시:
      - device_tokens 조회 (is_active=TRUE)
      - FCM/APNs 어댑터 호출 (비동기 워커)
   e. quiet_hours 해당 시 푸시 SKIP, 인앱 알림은 그대로 저장
3. FCM/APNs 응답:
   - 실패 유형별:
     · InvalidRegistration / NotRegistered → device_tokens.is_active=FALSE
     · MismatchSenderId → 로그 + 운영 알람
     · Retriable 오류 → exponential backoff 최대 3회
```

### 3.8 발송 정책 매트릭스

| 조건 | 인앱 저장 | 푸시 발송 |
|---|:---:|:---:|
| `push_enabled = FALSE` | O | X |
| `types_json[type] = FALSE` | O | X |
| `quiet_hours` 해당 시간 | O | X (지연 발송 없음 — drop) |
| 활성 토큰 0개 | O | - |
| 자기 자신에게 오는 INTERACTION | X | X |
| `ANNOUNCEMENT` (broadcast) | O (대량 INSERT) | 설정 따라 O |

- **인앱 알림은 설정과 무관하게 항상 저장** (알림센터 이력 보존). 푸시만 설정 영향.

### 3.9 공지 대량 발송 — `ANNOUNCEMENT` 트리거

- **대상 화면**: Notice 상세는 [10-announcement-spec.md §3.2](./10-announcement-spec.md) 참조.
- **동작**:
  - 관리자가 공지 발행 시 `send_push=TRUE` 옵션이면 전 회원 대상 `notifications` INSERT + 활성 토큰 푸시.
  - **배치 처리** — 회원 수 10만 이상 시 `INSERT ... SELECT` 로 한 번에 저장 + 청크 단위(1000건)로 푸시 전송.
  - **타겟팅** — MVP 는 전체. Phase 4+ 에서 지역/관심사 타겟팅.

---

## 4. 비즈니스 규칙 & 제약

### 4.1 도메인 불변식

1. **인앱 알림은 설정과 무관하게 저장** — 알림센터 이력은 보존. 푸시만 설정 영향.
2. **자기 자신에게 오는 상호작용 알림은 발생하지 않음** (`REVIEW_LIKED` 등 발신자 ≠ 수신자 검증).
3. **푸시 발송 실패는 일시 재시도 가능. InvalidRegistration 은 즉시 토큰 비활성화**.
4. **회원당 활성 device_token 은 플랫폼당 N개 허용** — 특별 제한 없음. 단, 동일 `token` 은 전역 UNIQUE.
5. **알림 90일 후 hard delete** — 배치 처리.
6. **점검 공지(Maintenance)는 이 도메인과 분리** — 스플래시 메타로 별도 처리.

### 4.2 입력 제약

| 필드 | 제약 |
|---|---|
| `title` | 1~100자 |
| `body` | 1~300자 (푸시 페이로드 제한 고려) |
| `payload_json` | 직렬화 2KB 이하 |
| `types` (설정) | Enum 내 키만 허용, unknown 무시 |
| `quietHoursStart/End` | `HH:mm` 포맷, KST |
| `token` (디바이스) | 1~500자 |

### 4.3 푸시 페이로드 규격 — 어댑터 레이어

클라이언트-무관 추상 페이로드:

```kotlin
data class PushPayload(
    val title: String,
    val body: String,
    val data: Map<String, String>,  // deepLink 등
    val badgeCount: Int?,            // iOS badge
    val category: String?            // APNs category / FCM category
)
```

- **FCM 어댑터** — `message.notification` + `data` 로 분리.
- **APNs 어댑터** — `aps.alert` + `aps.badge` + custom key(`data`).
- **공통 모듈** — `domain/` 또는 `common/` 에 `PushNotifier` 인터페이스 정의 → `FcmPushNotifier`, `ApnsPushNotifier` 구현.

### 4.4 방해금지(Quiet Hours) 판정 로직

```kotlin
fun isQuietHours(now: Instant, start: LocalTime?, end: LocalTime?): Boolean {
    if (start == null || end == null) return false
    val kst = now.atZone(ZoneId.of("Asia/Seoul")).toLocalTime()
    return if (start <= end) {
        kst in start..end
    } else {
        // 22:00 ~ 08:00 같은 자정 넘김
        kst >= start || kst <= end
    }
}
```

### 4.5 회수/취소 정책

- **인앱 알림 회수** — 관리자가 잘못된 알림 발송 시 해당 row `deleted_at = NOW()`. 푸시는 회수 불가(이미 OS 에 도달).
- **회수 UI** — MVP 에선 admin-api 의 `POST /admin/api/v1/notifications/{id}/recall` 제공 (Phase 4+).

---

## 5. API 엔드포인트 초안 (REST)

### 5.1 app-api (회원용)

| Method | Path | Auth | 설명 |
|---|---|:---:|---|
| GET | `/api/v1/notifications` | accessToken | 내 알림 목록 |
| GET | `/api/v1/notifications/unread-count` | accessToken | 안읽음 뱃지 |
| POST | `/api/v1/notifications/{id}/read` | accessToken | 단건 읽음 |
| POST | `/api/v1/notifications/read-all` | accessToken | 전체 읽음 |
| DELETE | `/api/v1/notifications/{id}` | accessToken | 단건 삭제 |
| DELETE | `/api/v1/notifications` | accessToken | 전체 삭제 |
| GET | `/api/v1/notifications/settings` | accessToken | 설정 조회 |
| PATCH | `/api/v1/notifications/settings` | accessToken | 설정 변경 |
| POST | `/api/v1/notifications/devices` | accessToken | 디바이스 토큰 등록/갱신 |
| DELETE | `/api/v1/notifications/devices/{token}` | accessToken | 디바이스 토큰 비활성화 |

### 5.2 admin-api (관리자용)

| Method | Path | 설명 |
|---|---|---|
| POST | `/admin/api/v1/notifications/broadcast` | 전체 발송 (announcement/system) |
| POST | `/admin/api/v1/notifications/{id}/recall` | 알림 회수 |
| GET | `/admin/api/v1/notifications/stats` | 발송/오픈율 통계 (Phase 4+) |

### 5.3 응답 예시

#### `GET /notifications`

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
  "nextCursor": "eyJpZCI6NDk5OX0",
  "hasNext": true
}
```

#### `GET /notifications/settings`

```json
{
  "pushEnabled": true,
  "types": {
    "REVIEW_LIKED": true,
    "ANNOUNCEMENT": false,
    "INQUIRY_ANSWERED": true,
    "SYSTEM": true
  },
  "quietHoursStart": "22:00",
  "quietHoursEnd": "08:00"
}
```

---

## 6. 에러 코드

`NOTIFICATION_{NNN}`.

| 코드 | HTTP | 메시지 | 발생 상황 |
|---|:---:|---|---|
| `NOTIFICATION_001` | 404 | 알림을 찾을 수 없어요. | 없는 id, 이미 삭제됨 |
| `NOTIFICATION_002` | 403 | 본인의 알림만 조회·변경할 수 있어요. | 타인 알림 접근 |
| `NOTIFICATION_003` | 400 | 알림 설정값을 확인해주세요. | types_json unknown key, 시간 포맷 오류 |
| `NOTIFICATION_004` | 400 | 디바이스 토큰 형식이 올바르지 않아요. | platform Enum 외, token 길이 |
| `NOTIFICATION_005` | 400 | 방해금지 시간 형식이 올바르지 않아요. | HH:mm 파싱 실패 |
| `NOTIFICATION_006` | 409 | 동일 토큰이 다른 계정에 이미 등록되어 있어요. | UPSERT 충돌 시 이관 거부 정책 적용 ❓ |

> `NOTIFICATION_006` 은 "토큰 이관"을 허용할지(묵시적 UPSERT) 거부할지(명시적 API)에 따라 사용 여부가 달라짐. MVP 는 **묵시적 UPSERT**로 `NOTIFICATION_006` 미발생 제안.

---

## 7. 엣지 케이스 & 예외 처리

| 케이스 | 처리 |
|---|---|
| **같은 토큰을 다른 회원이 등록** | UPSERT — `member_id` 이관, 기존 회원에게는 비활성화 효과. |
| **앱 삭제/재설치로 토큰 변경** | 신규 토큰 등록 + 기존 토큰은 FCM/APNs 응답으로 invalid 감지 → `is_active=FALSE`. |
| **InvalidRegistration/NotRegistered 응답** | 해당 `device_tokens.is_active = FALSE` + `last_active_at` 갱신. 인앱 알림은 영향 없음. |
| **푸시 레이트 리밋 초과** | 기본 재시도 + 백오프. 3회 실패 시 skip + 에러 로그. 인앱 알림은 유지. |
| **quiet_hours 중 푸시** | drop (지연 발송 MVP 에선 미지원). 다음날 동일 이벤트가 와도 별도. |
| **탈퇴 유예 중 푸시** | 허용 (ACTIVE). 탈퇴 완료 시 `notifications`, `device_tokens`, `notification_settings` hard delete. |
| **알림 설정 row 없음** | 기본값(`push_enabled=true`, 모든 타입 on) 으로 해석. 첫 PATCH 시 row 생성. |
| **대량 broadcast 중 일부 회원 탈퇴** | `INSERT ... SELECT ... FROM members WHERE account_status = 'ACTIVE'` 필터로 해결. |
| **푸시 실패에도 인앱은 OK 로 간주** | 이중 저장 전략 — DB 트랜잭션은 인앱 저장에만. 푸시는 after-commit 훅 또는 outbox 패턴. |
| **동일 이벤트 중복 발행** | 이벤트 dedupe key (`REVIEW_LIKED:{reviewId}:{likerId}`) 로 중복 방지. 24h 내 동일 key 재발생 시 drop ❓ — MVP 제안. |
| **`ANNOUNCEMENT` 를 발행했는데 설정에서 off** | 인앱 저장은 유지. 푸시는 개별 설정 존중. |
| **본인 리뷰에 본인이 좋아요 (금지)** | 애초에 Review 도메인이 `REVIEW_008` 로 차단 — 이벤트 자체 발행되지 않음. |

---

## 8. 성능 / 인덱싱 고려사항

### 8.1 주요 쿼리 패턴

| 패턴 | 빈도 | 인덱스 |
|---|:---:|---|
| 내 알림 목록 | 매우 높음 | `idx_notifications_member_created` |
| 안읽음 카운트 | 매우 높음 (홈 진입마다) | `idx_notifications_member_unread` 부분 |
| 발송 시 활성 토큰 조회 | 매우 높음 | `idx_device_tokens_member_active` |
| 설정 조회 | 높음 | PK `(member_id)` |
| 단건 읽음 처리 | 중간 | PK |

### 8.2 캐시

| 캐시 | 키 | TTL | 무효화 |
|---|---|---|---|
| 안읽음 카운트 | `notifications:unread:{memberId}` | 30초 | 알림 생성/읽음/삭제 시 evict |
| 알림 설정 | `notifications:settings:{memberId}` | 10분 | PATCH 시 evict |

- 알림 목록 자체는 캐시하지 않음 (실시간성).

### 8.3 대량 발송 전략

- 전체 방송 시 `notifications` INSERT 는 **INSERT ... SELECT** 한 번으로 처리 (10만 회원 1초 이내 목표).
- 푸시 발송은 **비동기 워커** + 청크 단위(1000건) 배치 FCM/APNs 호출.
- FCM 는 multicast(1000개 토큰/1회) 지원 → 활용 권장.
- APNs 는 HTTP/2 세션 재사용으로 병렬성 확보.

### 8.4 정리 배치

- 매일 03:00 KST:
  - `notifications WHERE created_at < NOW() - INTERVAL '90 days'` hard delete.
  - `device_tokens WHERE is_active = FALSE AND last_active_at < NOW() - INTERVAL '30 days'` hard delete.
  - 활성 토큰 중 `last_active_at < NOW() - INTERVAL '180 days'` → `is_active = FALSE` 처리.

### 8.5 동시성

- 읽음 처리 레이스: `UPDATE ... WHERE id = ? AND is_read = FALSE` 조건부 UPDATE로 멱등성 확보.
- 안읽음 카운트: 정확한 값은 쿼리 기반. 캐시는 TTL 만료 시 재계산.

---

## 9. 의존성 & 향후 확장

### 9.1 의존 도메인

- **Member(D3)** — FK.
- **Review(D9)** — `REVIEW_LIKED` 이벤트 발행.
- **Announcement(D2/공지)** — `ANNOUNCEMENT` 발행.
- **Inquiry(D12)** — `INQUIRY_ANSWERED` 발행.
- **외부** — FCM (Android), APNs (iOS). 별도 Firebase Service Account, APNs key 설정 필요.

### 9.2 인프라 설정 (Phase 2 진입 전 확인)

| 항목 | 내용 |
|---|---|
| FCM 프로젝트 | Firebase Console 에서 생성, Service Account JSON 발급 |
| APNs | Apple Developer Console Key (.p8) + Team ID + Key ID + Bundle ID |
| 환경변수 | `app.notification.fcm.service-account-json`, `app.notification.apns.team-id`, `apns.key-id`, `apns.bundle-id`, `apns.key-p8-path` 등 |
| 워커 | 별도 프로세스 vs 인프로세스 `@Async` — MVP 는 `@Async` + Spring `@TransactionalEventListener` 제안 |

### 9.3 확장 포인트

| 확장 | Phase | 비고 |
|---|:---:|---|
| 알림 카테고리 세분화 (리뷰답글, 찜한 업체 소식) | Phase 3+ | Enum 추가 |
| 지연 발송 (quiet_hours 종료 후 예약 발송) | Phase 3+ | 큐 기반 schedule |
| 마케팅 푸시 + 동의 체크 | Phase 3+ | `members.marketing_consent` 연계 |
| 타겟팅 발송 (지역/관심사/Pet Species) | Phase 4+ | broadcast 쿼리 확장 |
| 알림 A/B 테스트 | Phase 5+ | variant 필드 |
| 웹 푸시 (PWA/브라우저) | Phase 5+ | VAPID |
| SMS/이메일 fallback | 범위 밖 | 별도 채널 |
| 발송 이력 로그 (`notification_send_logs`) | Phase 4+ | 운영 디버깅 |

### 9.4 ❓ 확인 필요 항목

1. **인앱 알림 자동 읽음 타이밍** — 리스트 진입 시 전체 읽음 처리할지 / 개별 탭 시만 할지.
2. **`NotificationType.Group` 과 개별 타입 중 설정 UI 단위** — 그룹 단위 스위치만 제공할지 개별 타입까지 열지.
3. **중복 이벤트 dedupe 정책** — 24h 내 동일 (event_key) 재발생 drop? 아니면 매번 알림?
4. **`ANNOUNCEMENT` 푸시 발송 기본값** — 공지 발행 시 기본 push 보낼지, 체크박스로 선택할지.
5. **방해금지 지연 발송 여부** — MVP 는 drop. quiet_hours 종료 시 재발송 큐는 Phase 3+.
6. **푸시 실패 재시도 정책** — 3회 + exponential? 구체 수치 확정.
7. **토큰 이관 시 기존 회원에게 "다른 기기에서 로그인됨" 인앱 알림** — 보안 알림으로 제공할지 ❓.
8. **관리자 알림 회수 UI 노출 범위** — admin-api 만? app-api 에서는 조회만 가능?

---

## 10. 관련 문서

- [00. 서비스 개요](./00-overview.md)
- [01. 도메인 맵](./01-domain-map.md)
- [02. 용어집](./02-glossary.md)
- [03. 공통 정책](./03-common-policies.md)
- [06. 리뷰(Review) 기능 명세](./06-review-spec.md) — `REVIEW_LIKED` 이벤트 소스
- [10. 공지/FAQ/약관 명세](./10-announcement-spec.md) — `ANNOUNCEMENT` 이벤트 소스
- [11. 1:1 문의 명세](./11-inquiry-spec.md) — `INQUIRY_ANSWERED` 이벤트 소스
