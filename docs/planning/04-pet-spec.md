# 04. 반려동물(Pet) 기능 명세

> 본 문서는 로카펫의 **D4. 반려동물 관리(Pet)** 도메인 상세 기획이다.
> 상위 문서: [01. 도메인 맵](./01-domain-map.md), [02. 용어집](./02-glossary.md), [03. 공통 정책](./03-common-policies.md)

---

## 1. 개요

### 1.1 도메인 책임

- 회원이 자신의 반려동물 프로필을 **등록 · 조회 · 수정 · 삭제**하는 기능을 제공한다.
- 한 회원이 여러 마리의 반려동물을 등록할 수 있으며, 그 중 **1마리는 "대표 반려동물"** 로 지정된다.
- 대표 반려동물의 속성(Species, Size)은 **맞춤리뷰 필터링의 기준값**이 된다.
- 반려동물 정보는 **리뷰 작성 시 스냅샷으로 복제**되어, 이후 수정·삭제에 리뷰가 영향받지 않는다.

### 1.2 다른 도메인과의 관계

| 방향 | 대상 도메인 | 관계 |
|---|---|---|
| 참조 | Member(D3) | `pets.member_id` → FK |
| 피참조 | Review(D9) | `review_pets.pet_id` → FK (단, 스냅샷으로 복제) |
| 피참조 | Search(D7) / Feed(D6) | 대표 반려동물의 Species/Size 를 **맞춤리뷰 컨텍스트**로 사용 |
| 독립 | Place(D5) | 직접 관계 없음 (Review를 통한 간접 연결) |

---

## 2. 엔티티 & 데이터 모델

### 2.1 `pets` 테이블

| 컬럼 | 타입 | NOT NULL | 기본값 | 설명 |
|---|---|:---:|---|---|
| `id` | BIGSERIAL | O | - | PK |
| `member_id` | BIGINT | O | - | FK → `members.id` |
| `name` | VARCHAR(20) | O | - | 반려동물 이름 (닉네임 수준) |
| `species` | VARCHAR(10) | O | - | `DOG` / `CAT`. **등록 후 변경 불가** |
| `breed` | VARCHAR(50) | X | NULL | 품종 (자유 텍스트 또는 사전 정의 목록) |
| `gender` | VARCHAR(10) | O | - | `MALE` / `FEMALE` / `UNKNOWN` |
| `birth_date` | DATE | X | NULL | 생년월일. 타임존 불필요 — `LocalDate` |
| `is_birth_date_estimated` | BOOLEAN | O | FALSE | 입양 시점만 알 경우 추정값 표시용 |
| `weight_kg` | NUMERIC(4,1) | X | NULL | 0.1kg 단위. 고양이도 기록 가능 |
| `size` | VARCHAR(10) | O | - | `SMALL` / `MEDIUM` / `LARGE`. 회원 입력값 |
| `is_neutered` | BOOLEAN | X | NULL | 중성화 여부. `NULL` = 모름 |
| `profile_image_url` | VARCHAR(500) | X | NULL | S3/CloudFront URL (Presigned 업로드 후 COMMIT) |
| `personality_tags` | VARCHAR(255) | X | NULL | 기질 태그 CSV (예: `"활발,낯가림,친화적"`). **맞춤리뷰 매칭엔 미사용** |
| `is_primary` | BOOLEAN | O | FALSE | 대표 반려동물 플래그 |
| `created_at` | TIMESTAMPTZ | O | NOW() | |
| `updated_at` | TIMESTAMPTZ | O | NOW() | |
| `deleted_at` | TIMESTAMPTZ | X | NULL | **소프트 삭제** 마커. `NULL` = 활성 |

### 2.2 Enum 정의

```kotlin
enum class Species { DOG, CAT }
enum class PetGender { MALE, FEMALE, UNKNOWN }
enum class PetSize { SMALL, MEDIUM, LARGE }    // ~10kg / 10~20kg / 20kg+
```

### 2.3 관계

- `Member (1) ── (N) Pet`  (탈퇴 시 Pet 처리 정책은 [03-common-policies.md §2.1](./03-common-policies.md) 참조 — 탈퇴 완료 시 삭제)
- `Pet (N) ── (M) Review`  via `review_pets` (스냅샷 저장, [06-review-spec.md](./06-review-spec.md) 참조)

### 2.4 제약 조건 & 인덱스

| 종류 | 컬럼/표현 | 목적 |
|---|---|---|
| FK | `member_id → members(id)` | 소유권 |
| CHECK | `weight_kg >= 0 AND weight_kg <= 200` | 현실적 범위 방어 |
| CHECK | `char_length(name) BETWEEN 1 AND 20` | |
| UNIQUE (부분) | `UNIQUE (member_id) WHERE is_primary = TRUE AND deleted_at IS NULL` | 회원당 활성 대표 1마리 보장 |
| INDEX | `idx_pets_member_active (member_id) WHERE deleted_at IS NULL` | 회원별 활성 목록 조회 |
| INDEX | `idx_pets_species_size (species, size)` | 맞춤리뷰 집계 시 보조 (추후 Review 집계 조인) |

### 2.5 상태 머신

Pet 에는 명시적 상태 컬럼이 없고, `deleted_at` 으로만 분기한다.

```
  ┌──────────┐   delete    ┌───────────┐
  │  ACTIVE  │────────────▶│  DELETED  │  (soft)
  │          │             │           │
  │deleted_at│             │deleted_at │
  │  = NULL  │             │ = <ts>    │
  └──────────┘             └───────────┘
        ▲                         │
        │      (MVP 범위 밖)      │
        └─────────────────────────┘
             복구는 미지원
```

- **재활성화(Undelete)는 MVP 범위 밖.** 필요 시 신규 Pet 으로 재등록.

---

## 3. 주요 기능 (Use Case)

### 3.1 반려동물 등록 (10단계 위저드)

- **대상 화면**:
  [반려동물 추가 진입](node-id=479:42184) →
  [반려동물 추가 카드](node-id=479:42359) →
  [선택(개/고양이)](node-id=479:42534) →
  [등록 1단계](node-id=565:28473) →
  [2](node-id=565:29921) →
  [3](node-id=565:30231) →
  [4](node-id=565:30544) →
  [5](node-id=565:31150) →
  [6](node-id=565:31460) →
  [7](node-id=565:32069) →
  [8](node-id=565:32382) →
  [9](node-id=565:32695) →
  [10](node-id=565:33013) →
  [등록 완료](node-id=565:33626) /
  [고양이 플로우 분기](node-id=565:33940)

- **사전조건**: 회원 로그인, `onboardingStage = COMPLETED`.
- **사후조건**: `pets` 에 1 row 추가. 첫 등록이면 자동으로 `is_primary = TRUE`.

#### 단계별 수집 항목 (Figma 기반 추정, 클라이언트 위저드)

| 단계 | 수집 항목 | 비고 |
|:---:|---|---|
| 1 | **종(Species) 선택** — DOG / CAT | 이후 변경 불가. 고양이 선택 시 일부 단계 스킵 가능 (❓클라이언트 플로우 확인 필요) |
| 2 | **이름(name)** | 1~20자 |
| 3 | **품종(breed)** | 자유 텍스트 또는 사전 목록 (사전 목록 제공 여부 ❓) |
| 4 | **성별(gender)** + **중성화(is_neutered)** | |
| 5 | **생년월일(birth_date)** 또는 "추정 나이" | 정확히 모를 때 추정 체크 |
| 6 | **체중(weight_kg)** | 선택. 고양이는 일반적으로 기재 |
| 7 | **크기(size)** — SMALL / MEDIUM / LARGE | 체중 기준 자동 제안 + 유저 확정 |
| 8 | **기질/성격 태그(personality_tags)** | 다중 선택. **맞춤리뷰 매칭에는 미사용** (저장만) |
| 9 | **프로필 사진** | S3 Presigned 업로드 (1장) |
| 10 | **최종 확인 / 저장** | 입력값 요약 후 제출 |

> ❓ **고양이 플로우 축약 지점 확인 필요**: 고양이 선택 시 7단계(크기) 는 "실내묘 기준 SMALL 고정" 처리할지, 혹은 건너뛰고 서버 기본값 `SMALL` 주입할지 기획 확정 필요. 현재는 **항상 모든 필드를 수집하되, 고양이 기본 크기는 SMALL 로 프리셀렉트**하는 안을 제안.

#### 비즈니스 규칙

- **첫 등록 시 자동 대표 지정** — 기존 활성 Pet 이 0 마리이면 `is_primary = TRUE` 강제.
- **최대 등록 수**: **10마리** 제안 (활성 기준). 초과 시 `PET_004`.
- 이름/품종은 공백 trim, 연속 공백 1칸으로 축소.
- 프로필 이미지 없이도 등록 가능 (`profile_image_url = NULL`). 클라이언트는 기본 이미지 렌더.

#### 에러 케이스

| 상황 | 에러 코드 | HTTP |
|---|---|---|
| 필수 필드 누락/형식 오류 | `VALIDATION_001` | 400 |
| species 값이 `DOG`/`CAT` 아님 | `PET_002` | 400 |
| 최대 등록 수 초과 | `PET_004` | 409 |
| 이미지 업로드 미완료 (COMMIT 되지 않은 imageId 사용) | `UPLOAD_002` | 400 |

- **권한**: 회원 본인 (accessToken 필수).

---

### 3.2 반려동물 목록 조회

- **대상 화면**: [반려동물 프로필 리스트](node-id=479:42895)
- **입력**: 없음 (회원 컨텍스트)
- **출력**: 활성 Pet 배열 (`deleted_at IS NULL`). 대표 Pet 이 배열 첫 번째로 정렬.
- **규칙**:
  - 정렬: `is_primary DESC, created_at ASC, id ASC`.
  - 삭제된 Pet 은 응답에서 제외.

---

### 3.3 반려동물 상세 조회

- **대상 화면**: [반려동물 프로필](node-id=479:42895)
- **입력**: `petId`
- **권한**: 본인 소유 Pet 만 (Ownership 검증)
- **에러**: 타인 Pet 조회 시 `FORBIDDEN` → `PET_005` (403).

---

### 3.4 반려동물 정보 수정

- **대상 화면**: [프로필 수정](node-id=559:11639), [수정 상세](node-id=565:41425)
- **입력**: 수정 가능한 필드 (아래 "수정 불가 필드" 외 전부)
- **수정 불가 필드**:
  - `species` — 한 번 정한 종은 변경 불가 (리뷰 스냅샷과의 정합성).
  - `member_id` — 소유자 이전 미지원.
- **수정 시 스냅샷 영향 없음**: 기존 Review의 `review_pets.*_snapshot` 은 불변.
- **에러**: species 변경 시도 시 `PET_003` (400).

---

### 3.5 대표 반려동물 설정/변경

- **대상 화면**: [대표 설정](node-id=559:11508), [대표 설정 상세](node-id=565:41734), [대표 변경](node-id=479:42978)
- **사전조건**: 활성 Pet 이 2마리 이상.
- **동작**: 트랜잭션 내에서
  1. `UPDATE pets SET is_primary = FALSE WHERE member_id = ? AND is_primary = TRUE`
  2. `UPDATE pets SET is_primary = TRUE WHERE id = ? AND member_id = ?`
- **제약**: 활성 Pet 이 0 마리가 되는 조합은 불가능 (삭제 로직에서 방어).

---

### 3.6 반려동물 삭제

- **동작**: **소프트 삭제** (`deleted_at = NOW()`).
- **사전조건**:
  - 대표 Pet 삭제 시 남은 활성 Pet 중 1마리를 새 대표로 자동 승격. 남은 Pet 이 없으면 그대로 삭제 완료(대표 없음 상태).
- **리뷰와의 관계**:
  - `review_pets.pet_id` FK 는 유지 (ON DELETE 동작 없음 — 소프트 삭제 전제).
  - 리뷰 조회 시 `pet_name_snapshot`/`species_snapshot`/`size_snapshot` 값을 그대로 노출.
- **탈퇴 시**: [03-common-policies.md §2.1](./03-common-policies.md) — 탈퇴 완료 시 Pet 은 hard delete.

#### 에러 케이스

| 상황 | 에러 코드 | HTTP |
|---|---|---|
| 대상 Pet 없음 / 이미 삭제됨 | `PET_001` | 404 |
| 본인 소유 아님 | `PET_005` | 403 |

---

## 4. 비즈니스 규칙 & 제약

### 4.1 도메인 불변식

1. **회원당 활성 대표 Pet 은 최대 1마리**. (부분 UNIQUE 인덱스로 물리 보장)
2. **활성 Pet 이 1마리 이상이면 그 중 반드시 1마리는 대표**. (애플리케이션 로직 책임 — 삭제/등록 시 유지)
3. **Species 는 등록 후 불변**.
4. **리뷰에 연결된 Pet 은 삭제해도 리뷰 스냅샷은 영향 없음**.

### 4.2 입력 제약

| 필드 | 제약 |
|---|---|
| `name` | 1~20자 (trim 후), 개행/이모지 제한 (❓이모지 허용 여부 정책 확인) |
| `breed` | 0~50자 |
| `weight_kg` | 0 < weight ≤ 200 (NUMERIC(4,1)) |
| `personality_tags` | 최대 8개, 각 태그 1~10자 |
| 등록 수 | 회원당 활성 최대 10마리 |
| 프로필 이미지 | 1장, 최대 10MB (공통 정책 §5.2) |

---

## 5. API 엔드포인트 초안 (REST)

| Method | Path | Auth | 설명 |
|---|---|:---:|---|
| POST | `/api/v1/pets` | accessToken | 반려동물 등록 |
| GET | `/api/v1/pets` | accessToken | 내 반려동물 목록 |
| GET | `/api/v1/pets/{petId}` | accessToken | 반려동물 상세 |
| PATCH | `/api/v1/pets/{petId}` | accessToken | 수정 (species 제외) |
| DELETE | `/api/v1/pets/{petId}` | accessToken | 소프트 삭제 |
| POST | `/api/v1/pets/{petId}/primary` | accessToken | 대표 지정 |

### 5.1 요청 바디 스키마 (POST `/api/v1/pets`)

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
  "personalityTags": ["활발", "친화적"],
  "profileImageId": "img_01HX..."
}
```

### 5.2 응답 바디 스키마 (공통 PetResponse)

```json
{
  "id": 12,
  "name": "초코",
  "species": "DOG",
  "breed": "포메라니안",
  "gender": "MALE",
  "birthDate": "2022-05-01",
  "isBirthDateEstimated": false,
  "weightKg": 3.2,
  "size": "SMALL",
  "isNeutered": true,
  "personalityTags": ["활발", "친화적"],
  "profileImageUrl": "https://cdn.locapet.app/pets/12/img_01HX...jpg",
  "isPrimary": true,
  "createdAt": "2026-04-18T09:30:00Z",
  "updatedAt": "2026-04-18T09:30:00Z"
}
```

### 5.3 대표 지정 응답

```json
{ "petId": 12, "isPrimary": true }
```

---

## 6. 에러 코드

`03-common-policies.md §6.2` 컨벤션에 따라 `PET_{NNN}` 형식.

| 코드 | HTTP | 메시지 (유저용) | 발생 상황 |
|---|:---:|---|---|
| `PET_001` | 404 | 반려동물을 찾을 수 없어요. | 없는 petId, 삭제된 Pet |
| `PET_002` | 400 | 지원하지 않는 반려동물 종이에요. | species ∉ {DOG, CAT} |
| `PET_003` | 400 | 반려동물 종은 변경할 수 없어요. | 수정 요청에 species 포함 또는 변경 감지 |
| `PET_004` | 409 | 등록할 수 있는 반려동물 수를 초과했어요. | 활성 Pet 10마리 초과 |
| `PET_005` | 403 | 본인의 반려동물만 관리할 수 있어요. | 타인 Pet 접근 |
| `PET_006` | 400 | 대표 반려동물은 1마리만 지정할 수 있어요. | 동시성으로 인한 드문 케이스 |
| `PET_007` | 400 | 이 반려동물은 이미 삭제되었어요. | `deleted_at IS NOT NULL` 대상에 수정/삭제 시도 |

---

## 7. 엣지 케이스 & 예외 처리

| 케이스 | 처리 |
|---|---|
| **첫 로그인 직후 Pet 0마리** | 홈에서 "반려동물 등록" 배너 노출 (클라이언트 책임). 서버는 빈 배열 반환. |
| **대표 Pet 삭제** | 트랜잭션 내에서 남은 활성 Pet 중 가장 오래된(created_at 오름) 것을 자동 승격. 없으면 `is_primary` 없는 상태 허용. |
| **대표 Pet 재지정을 현재 대표에 요청** | No-op. 200 반환. |
| **삭제된 Pet 을 리뷰 작성 시 선택** | 불가 — 리뷰 작성 API 가 `deleted_at IS NULL` 필터링. |
| **Species 변경 시도** | 400 `PET_003` 즉시 차단. 리뷰 정합성 보호. |
| **동시성 — 두 디바이스에서 대표 지정** | 트랜잭션 + 부분 UNIQUE 제약으로 마지막 요청이 승리, 패배 요청은 `PET_006` 또는 재시도. |
| **프로필 이미지 업로드 중 앱 종료** | COMMIT 되지 않은 이미지는 24h 후 배치 삭제 (공통 정책 §5.4). |
| **탈퇴 유예 중 Pet 조회/수정** | 허용 (유예 중 = ACTIVE). 탈퇴 완료 시 Pet 전체 삭제. |

---

## 8. 성능 / 인덱싱 고려사항

### 8.1 예상 쿼리 패턴

| 패턴 | 빈도 | 인덱스 |
|---|:---:|---|
| `SELECT * FROM pets WHERE member_id = ? AND deleted_at IS NULL` | **매우 높음** (앱 진입 시마다) | `idx_pets_member_active` |
| `SELECT * FROM pets WHERE member_id = ? AND is_primary = TRUE AND deleted_at IS NULL` | 매우 높음 (맞춤리뷰 컨텍스트) | 위 인덱스로 충분 (커버) |
| `SELECT * FROM pets WHERE id = ? AND member_id = ?` | 높음 (상세/수정) | PK + 조건 필터 |

### 8.2 캐시

- **대표 Pet 캐시**: `pet:primary:{memberId}` — TTL 5분. 맞춤리뷰 필터링 시 매번 DB 조회를 피하기 위함.
- 무효화 트리거: 등록, 수정, 삭제, 대표 변경 시 해당 키 삭제.
- ❓ 캐시 미적용으로도 성능 충분할 수 있음 — 실측 후 도입 결정.

### 8.3 N+1 주의

- 회원 목록 + 각 회원의 대표 Pet 을 admin-api 에서 노출 시, `JOIN` 또는 batch fetch 로 해결.

---

## 9. 의존성 & 향후 확장

### 9.1 의존 도메인

- **Member(D3)** — FK 소유자.
- **Upload(공통)** — 프로필 이미지 Presigned 업로드 플로우.

### 9.2 확장 포인트

| 확장 후보 | Phase | 비고 |
|---|:---:|---|
| 품종 사전 목록(Master Table) | Phase 3+ | 자동완성 + 통계용. MVP 는 자유 텍스트. |
| 기질 태그 기반 맞춤리뷰 고도화 | Phase 4+ | 현재 매칭은 Species+Size만 사용. |
| 건강정보(알러지/질병) | 범위 밖 | Out of Scope (00-overview.md §5.3) |
| 반려동물 SNS/공유 프로필 | Phase 5+ | 공개 프로필 URL. |
| 추가 Species (토끼, 페럿 등) | 미정 | Enum 확장 + 카테고리/PetPolicy 동시 확장 필요. |

### 9.3 ❓ 확인 필요 항목 (Phase 3 진입 전 결정 권장)

1. 고양이 등록 플로우에서 **실제 스킵되는 단계 번호** — 클라이언트 기획자와 확정.
2. **품종 사전 목록** 제공 여부 — 없다면 텍스트 자유 입력 허용 정책.
3. **이모지/특수문자 허용 범위** — name 필드.
4. **대표 Pet 자동 승격 정렬 기준** — 현재 제안: `created_at ASC`. UX상 "가장 최근에 등록한 Pet" 이 자연스러울 수도 있음.
5. **최대 등록 수 10마리** 적정성 — 운영 데이터 축적 후 조정.
