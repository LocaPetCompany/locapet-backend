# 13. 화면-API 매핑표 (Screen-to-API Map)

> 본 문서는 Figma 125개 화면을 **12개 그룹**으로 분류하고, 각 화면에서 호출되는 API 를 표로 정리한다.
> 목적: **Phase 5 API 설계 시 화면 단위로 필요한 엔드포인트를 누락 없이 식별**.
>
> 상위 문서: [12. UX 플로우](./12-ux-flows.md) — 플로우 관점의 상태 전이와 분기
> 연관 문서: [04](./04-pet-spec.md)~[11](./11-inquiry-spec.md) 도메인별 기획서

---

## 0. 개요 · 표기 규칙

### 0.1 표기 규칙

- **API 경로**: `GET /api/v1/...`, `POST /admin/api/v1/...` (절대 경로)
- **인증(Auth)** 열:
  - `none` — 비로그인 허용
  - `access` — `accessToken` 필수 (JWT, type=ACCESS)
  - `onboarding` — `onboardingAccessToken` 또는 `onboardingToken` 필수
  - `admin` — admin-api 인증 (ID/PW + IP 화이트리스트, 사용자 확정)
- **진입 시 API** — 화면이 렌더될 때 자동 호출되는 API
- **액션 시 API** — 버튼/인터랙션 트리거 API
- **?** 마크 — Figma node id 는 기획서에 등장했지만 화면 구성 상 추정인 경우 (발굴 시점이 Phase 5 )

### 0.2 그룹 구성 (12그룹, 총 125화면)

| # | 그룹명 | 화면 수 (추정) | 주요 도메인 |
|---|---|---:|---|
| 1 | 인증/온보딩 | 11 | Auth, Meta |
| 2 | 홈/피드 | 4 | Feed, Place |
| 3 | 검색/필터 | 22 | Search, Place |
| 4 | 지도/내 주변 | 7 | Place, Map |
| 5 | 업체 상세 | 9 | Place, Review, Favorite |
| 6 | 리뷰 (작성/수정/목록) | 15 | Review, Pet |
| 7 | 반려동물 관리 | 21 | Pet, Upload |
| 8 | 찜/최근 본 장소 | 5 | Favorite |
| 9 | 알림/설정 | 9 | Notification |
| 10 | 공지/FAQ/약관 | 9 | Announcement, Terms |
| 11 | 문의(1:1) | 11 | Inquiry |
| 12 | 마이페이지/기타/모달 | 2+ | Member, 공통 모달 |

> 합계: ~125. 그룹간 경계에 있는 화면(검색↔업체 상세 리스트 등)은 대표 그룹에만 배치하여 중복 제거.

---

## 1. 인증/온보딩 (11 화면)

| Figma ID | 화면명 | API (진입 시) | API (액션 시) | 인증 |
|---|---|---|---|---|
| 479:28444 | 온보딩 시작/스플래시 | `GET /api/v1/meta/splash` (앱 버전/유지보수/정책 URL) | — | none |
| 479:28512 | 소셜 로그인 | — | `POST /api/v1/auth/social/{provider}` | none |
| — | 본인인증 (네이티브 SDK) | — | `POST /api/v1/onboarding/identity/verify` | onboarding (`onboardingToken` body) |
| 479:28476 | 프로필 설정 (닉네임+약관) | `GET /api/v1/terms` (현재 유효 약관) | `POST /api/v1/onboarding/profile/complete` | onboarding (`onboardingAccessToken`) |
| 479:28774 | 약관 및 정책 (리스트) | `GET /api/v1/terms` | — | none |
| 479:28780 | 약관 상세 1 | `GET /api/v1/terms/{id}` | — | none |
| 479:28806 | 약관 상세 2 | `GET /api/v1/terms/{id}` | — | none |
| — | 자동 복구 안내 모달 (`WITHDRAW_REQUESTED` 로그인 시) | — | — (로그인 응답 기반) | none |
| — | 강제 업데이트 모달 | — | 스토어 외부 링크 | none |
| — | 유지보수 차단 화면 | `GET /api/v1/meta/splash` 폴링 | — | none |
| — | 탈퇴 요청/확인 화면 (❓ Figma node 미확정) | — | `POST /api/v1/member/withdraw` | access |

**화면 진입 시 공통**:
- 앱 실행 직후 반드시 `GET /api/v1/meta/splash` → `AppVersion` · `Maintenance` · 정책 URL 확인
- 로그인 이후 `GET /api/v1/auth/session` 로 세션 상태 재검증 (라우팅 결정)

---

## 2. 홈/피드 (4 화면)

| Figma ID | 화면명 | API (진입 시) | API (액션 시) | 인증 |
|---|---|---|---|---|
| 479:26692 | 홈 메인 | `GET /api/v1/places/curated` + `GET /api/v1/places/trending?window=7d` + `GET /api/v1/notifications/unread-count` (로그인 시) | 섹션 카드 탭 → 상세 | none (로그인 시 `access`) |
| 479:42172 | 홈 헤더 (상단 고정) | — | — | - |
| 479:26910 | 요즘 찜 많은 장소 | `GET /api/v1/places/trending?window=7d&size=20` | — | none |
| 479:26933 | 로카 추천 장소 | `GET /api/v1/places/curated?size=20` | — | none |

**공통 사이드이펙트**:
- 로그인 상태면 홈 진입 시 `GET /api/v1/notifications/unread-count` (30초 Redis 캐시)
- 대표 Pet 배지 표시용 `GET /api/v1/pets` 1회 호출 (로그인 시)

---

## 3. 검색/필터 (22 화면)

| Figma ID | 화면명 | API (진입 시) | API (액션 시) | 인증 |
|---|---|---|---|---|
| 479:43541 | 검색 진입 (필터 미적용) | — | — | none |
| 479:44981 | 검색창 입력 | — | (자동완성은 MVP 범위 밖) | none |
| 565:37178 | 필터 바텀시트 (통합) | — | — | none |
| 565:37241 | 필터: 지역 | — | — | none |
| 565:37304 | 필터: 거리 | — | (위치 권한 요청) | none |
| 565:37368 | 필터: 허용 반려동물 | — | — | none |
| 565:37432 | 필터: 편의시설 (OR 연산, 사용자 확정) | — | — | none |
| 565:37496 | 필터: 평점 | — | — | none |
| 565:37560 | 필터: 영업중 (KST) | — | — | none |
| 479:44710 | 필터 적용 결과 | `GET /api/v1/places/search?...` | — | none |
| 479:44853 | 저장 필터 적용 후 결과 | `GET /api/v1/places/search?savedFilterId={id}&...` (overlay 우선) | — | access (savedFilterId 포함 시) |
| 480:48180 | 검색 결과 상세 1 | (업체 카드 탭 → 상세) | — | none |
| 479:43567 | 검색 결과 상세 7 | 동상 | — | none |
| 479:44304 | 저장하기 진입 | — | — | access |
| 565:38081 | 저장 플로우 A | — | — | access |
| 565:38218 | 저장 입력 완료 B | — | `POST /api/v1/saved-filters` | access |
| 479:44573 | 저장 완료 | — | — | access |
| 479:43756 | 저장된 필터 목록 | `GET /api/v1/saved-filters?sort=CREATED_AT_DESC` | — | access |
| 479:44035 | 저장된 필터 Empty | `GET /api/v1/saved-filters` → `items:[]` | — | access |
| 565:37098 | 저장 필터 바텀시트 A | `GET /api/v1/saved-filters` | — | access |
| 565:37130 | 저장 필터 바텀시트 B | 동상 | — | access |
| 610:48257 | 저장 필터 바텀시트 C | 동상 | — | access |
| 479:43895 | 저장 필터 적용 UI | — | `GET /api/v1/places/search?savedFilterId=...` → `last_applied_at` 갱신 | access |
| 603:41728 | 저장 필터 삭제 확인 | — | `DELETE /api/v1/saved-filters/{id}` | access |
| 479:44441 | 저장 필터 편집/다건 삭제 | — | `POST /api/v1/saved-filters/delete` | access |

**공통 사이드이펙트**:
- 검색 API (`/places/search`, `/places`) 는 비로그인 허용. 단 `savedFilterId` 파라미터는 `access` 필수

---

## 4. 지도/내 주변 (7 화면)

| Figma ID | 화면명 | API (진입 시) | API (액션 시) | 인증 |
|---|---|---|---|---|
| 479:27003 | 홈 → 내 주변 | (위치 권한 요청) `GET /api/v1/places/nearby?lat=...&lng=...&radius=3000` | — | none |
| 479:27025 | 내 주변 리스트 | `GET /api/v1/places?lat=...&lng=...&sort=distance` | — | none |
| 479:27071 | 내 주변 지도 | 동상 (같은 응답 재사용) | 지도 뷰포트 변경 시 재호출 (선택) | none |
| 565:46721 | 내 주변 확장 | 동상 | — | none |
| 522:36976 | 식당 카테고리 지도 | `GET /api/v1/places?category=RESTAURANT&lat=...&lng=...` | — | none |
| 522:37566 | 식당 카테고리 목록 | 동상 | — | none |
| 565:47221 | 로카유치원 지도 | `GET /api/v1/places?category=KINDERGARTEN&lat=...&lng=...` | — | none |

**공통**:
- `sort=distance` 파라미터 사용 시 `lat`/`lng` 없으면 `PLACE_005` (400)
- 지도 프로바이더: 네이버 지도 + WGS84 (사용자 확정)

---

## 5. 업체 상세 (9 화면)

| Figma ID | 화면명 | API (진입 시) | API (액션 시) | 인증 |
|---|---|---|---|---|
| 480:50352 | 업체 상세 | `GET /api/v1/places/{placeId}` + 백그라운드 `POST /api/v1/members/me/recently-viewed` (로그인 시) | — | none |
| 479:24162 | 상세 스크롤 | 동상 (같은 응답 캐시 사용) | — | none |
| 479:24418 | 상세 리뷰 섹션 | `GET /api/v1/places/{placeId}/reviews?sort=latest&size=20` | — | none |
| 479:23645 | 전체 리뷰 | 동상 (size=50) | 정렬 변경 → 재호출 | none |
| 479:23816 | 전체 리뷰 스크롤 | cursor 페이지네이션 | — | none |
| 479:26205 | 리뷰 스크롤 상세 | 동상 | — | none |
| 565:42655 | 맞춤리뷰만 토글 | `GET /api/v1/places/{placeId}/reviews?sort=tailored` | 대표 Pet 없으면 `REVIEW_006` 400 | access |
| 479:24707 | 카카오톡 채널 팝업 | — | 클라이언트 딥링크 (서버 호출 없음) | - |
| 603:42166 | 전화 팝업 | — | 클라이언트 `tel:` (비로그인 시 마스킹된 번호, 사용자 확정) | - |
| 479:25381 | 이메일 팝업 | — | 클라이언트 `mailto:` | - |
| 489:54774 | 예약 팝업 | — | 클라이언트 외부 링크 | - |
| 489:56526 | 신고 모달 | — | `POST /api/v1/places/{placeId}/report` (❓ 정확한 엔드포인트는 Report 도메인 통합 시 확정) | access |

**액션: 찜 토글** (상세 페이지 하트 아이콘):
- 추가: `POST /api/v1/places/{placeId}/favorite`
- 해제: `DELETE /api/v1/places/{placeId}/favorite`
- CLOSED 업체 추가 시 `PLACE_004` (409)

---

## 6. 리뷰 (작성/수정/목록) (15 화면)

| Figma ID | 화면명 | API (진입 시) | API (액션 시) | 인증 |
|---|---|---|---|---|
| 479:42852 | 리뷰 작성 A (진입) | `GET /api/v1/pets` (활성 Pet 수 확인) | — | access |
| 603:45278 | 리뷰 작성 B (별점) | — | — (로컬 상태) | access |
| 603:45369 | 리뷰 작성 C (Pet 선택) | — | — | access |
| 603:45483 | 리뷰 작성 D (사진+본문) | (Presigned 업로드) `POST /api/v1/uploads/presigned` | 제출: `POST /api/v1/reviews` | access |
| 479:42712 | 리뷰 작성 완료 | — | — | access |
| 479:42776 | 작성 취소/삭제 모달 | — | — (클라이언트 로컬) | access |
| 479:43215 | 나의 리뷰 목록 | `GET /api/v1/members/me/reviews?cursor=...` | — | access |
| 479:43311 | 나의 리뷰 상세 | `GET /api/v1/reviews/{id}` | — | access |
| 479:43424 | 리뷰 수정 진입 | `GET /api/v1/reviews/{id}` | — | access |
| 479:43075 | 리뷰 수정 | — | `PATCH /api/v1/reviews/{id}` | access |
| 479:43139 | 리뷰 수정 상세 | 동상 | — | access |
| 479:43521 | 리뷰 삭제 확인 | — | `DELETE /api/v1/reviews/{id}` | access |
| — | 리뷰 좋아요 | — | `POST`/`DELETE /api/v1/reviews/{id}/like` | access |
| — | 리뷰 신고 모달 | — | `POST /api/v1/reviews/{id}/report` | access |
| — | 리뷰 이미지 뷰어 | — | 클라이언트 렌더 | - |

**공통**:
- 본인 리뷰 좋아요 금지 (사용자 확정) — `REVIEW_008`
- 신고 5건 자동 HIDDEN (사용자 확정) — [06-review-spec.md §3.7](./06-review-spec.md) 는 3건 제안이었으나 사용자 확정값으로 조정 필요
- 연결 Pet 최대 5마리 (사용자 확정)

---

## 7. 반려동물 관리 (21 화면)

| Figma ID | 화면명 | API (진입 시) | API (액션 시) | 인증 |
|---|---|---|---|---|
| 479:42184 | 등록 진입 | `GET /api/v1/pets` | — | access |
| 479:42359 | 추가 카드 | — | — | access |
| 479:42534 | 개/고양이 선택 | — | — (로컬) | access |
| 565:28473 | 등록 Step 1 (종 선택) | — | — | access |
| 565:29921 | Step 2 (이름) | — | — | access |
| 565:30231 | Step 3 (품종) | — | — | access |
| 565:30544 | Step 4 (성별+중성화) | — | — | access |
| 565:31150 | Step 5 (생년월일) | — | — | access |
| 565:31460 | Step 6 (체중) | — | — | access |
| 565:32069 | Step 7 (크기) | — | — | access |
| 565:32382 | Step 8 (기질 태그) | — | — | access |
| 565:32695 | Step 9 (프로필 사진) | `POST /api/v1/uploads/presigned` | S3 PUT → imageId 수집 | access |
| 565:33013 | Step 10 (최종 확인) | — | `POST /api/v1/pets` | access |
| 565:33626 | 등록 완료 | — | — | access |
| 565:33940 | 고양이 분기 완료 | — | — | access |
| 479:42895 | 반려동물 프로필 리스트/상세 | `GET /api/v1/pets` + `GET /api/v1/pets/{id}` | — | access |
| 559:11639 | 프로필 수정 | `GET /api/v1/pets/{id}` | `PATCH /api/v1/pets/{id}` | access |
| 565:41425 | 수정 상세 | 동상 | — | access |
| 559:11508 | 대표 설정 | `GET /api/v1/pets` | `POST /api/v1/pets/{id}/primary` | access |
| 565:41734 | 대표 설정 상세 | 동상 | — | access |
| 479:42978 | 대표 변경 | 동상 | `POST /api/v1/pets/{id}/primary` | access |
| — | 반려동물 삭제 확인 (추정) | — | `DELETE /api/v1/pets/{id}` | access |

**공통**:
- 최대 10마리 (사용자 확정) — `PET_004` (409)
- 첫 Pet 자동 대표 승격, 대표 삭제 시 `created_at ASC` 로 자동 승격 (사용자 확정)
- `species` 변경 불가 — `PET_003` (400)

---

## 8. 찜/최근 본 장소 (5 화면)

| Figma ID | 화면명 | API (진입 시) | API (액션 시) | 인증 |
|---|---|---|---|---|
| 479:26968 | 찜 목록 | `GET /api/v1/members/me/favorites?cursor=...` | — | access |
| 479:26994 | 찜 Empty | 위와 동상 (`items:[]`) | — | access |
| 479:26749 | 최근 본 장소 | `GET /api/v1/members/me/recently-viewed?cursor=...` | — | access |
| 479:26764 | 선택 삭제 모드 | 동상 | `POST /api/v1/members/me/recently-viewed/delete` (다건) 또는 `DELETE /api/v1/members/me/recently-viewed/{placeId}` (단건) 또는 `DELETE /api/v1/members/me/recently-viewed` (전체) | access |
| 559:12184 | 최근 본 장소 Empty | 동상 | — | access |

**공통**:
- 최근 본 50개 LRU (사용자 확정)
- 업체 상세 진입 시 `POST /api/v1/members/me/recently-viewed` 자동 호출 (사용자 확정)

---

## 9. 알림/설정 (9 화면)

| Figma ID | 화면명 | API (진입 시) | API (액션 시) | 인증 |
|---|---|---|---|---|
| 479:26663 | 알림 목록 | `GET /api/v1/notifications?cursor=...&size=20` | 탭: `POST /api/v1/notifications/{id}/read`. 전체 읽음: `POST /api/v1/notifications/read-all`. 단건 삭제: `DELETE /api/v1/notifications/{id}`. 전체 삭제: `DELETE /api/v1/notifications` | access |
| 479:26451 | 알림 설정 | `GET /api/v1/notifications/settings` | `PATCH /api/v1/notifications/settings` | access |
| 479:47142 | 알림 설정 헤더 | 동상 | — | access |
| — | 알림 권한 요청 팝업 (OS 네이티브) | — | (허용 시) `POST /api/v1/notifications/devices` | access |
| — | 방해금지 시간 설정 | — | `PATCH /api/v1/notifications/settings { quietHoursStart, quietHoursEnd }` | access |
| — | 타입별 토글 | — | `PATCH /api/v1/notifications/settings { types: {...} }` | access |
| — | 알림 탭 → 리뷰 딥링크 | (딥링크 처리) | 리뷰 상세로 이동 | access |
| — | 알림 탭 → 공지 딥링크 | 공지 상세로 이동 | — | access |
| — | 알림 탭 → 문의 딥링크 | 문의 상세로 이동 | — | access |

**공통**:
- 알림 탭한 순간에만 읽음 처리 (사용자 확정)
- 방해금지 재발송 drop (사용자 확정)
- 24h dedupe (사용자 확정)
- 90일 정리 배치 (사용자 확정)
- 로그아웃 시 `DELETE /api/v1/notifications/devices/{token}` — 토큰 비활성화

---

## 10. 공지/FAQ/약관 (9 화면)

| Figma ID | 화면명 | API (진입 시) | API (액션 시) | 인증 |
|---|---|---|---|---|
| 479:26467 | 공지사항 목록 | `GET /api/v1/notices?cursor=...&size=20&category=ALL` | — | none |
| 479:26486 | 공지사항 Empty | 동상 (`items:[]`) | — | none |
| 479:26492 | 공지 상세 | `GET /api/v1/notices/{id}` | — | none |
| 479:28896 | 공지 복제본 | 동상 | — | none |
| 479:26501 | 자주 묻는 질문 | `GET /api/v1/faqs/categories` + `GET /api/v1/faqs?category=...` | 카테고리 탭 → 재호출 | none |
| 479:47146 | FAQ 헤더 | — | — | none |
| 479:28774 | 약관 및 정책 | `GET /api/v1/terms` | — | none |
| 479:28780 | 약관 상세 1 | `GET /api/v1/terms/{id}` | — | none |
| 479:28806 | 약관 상세 2 | `GET /api/v1/terms/{id}` | — | none |

**공통**:
- 재동의 대기 약관이 있으면 로그인 응답의 `pendingTermAgreements` 에 포함, 재동의 화면으로 자동 라우팅
- 재동의: `POST /api/v1/members/me/term-agreements`
- 선택 약관 철회: `DELETE /api/v1/members/me/term-agreements/{termId}`
- 공지 카테고리 4종(`GENERAL`/`UPDATE`/`EVENT`/`URGENT`) 고정 (사용자 확정)
- FAQ 카테고리 8종 (사용자 확정) — [10-announcement-spec.md §2.6](./10-announcement-spec.md)

---

## 11. 문의(1:1) (11 화면)

| Figma ID | 화면명 | API (진입 시) | API (액션 시) | 인증 |
|---|---|---|---|---|
| 479:27291 | 문의 작성 | — | — | access |
| 479:27312 | 유형 선택 모달 | — | — (로컬) | access |
| 522:28949 | 작성 변형 | — | — | access |
| 479:27377 | 카테고리 수정 변형 | — | — | access |
| — | 사진 첨부 (Presigned) | `POST /api/v1/uploads/presigned` | S3 PUT | access |
| — | 제출 | — | `POST /api/v1/inquiries` | access |
| 479:27334 | 작성 완료 | — | — | access |
| 522:28838 | 문의 목록 | `GET /api/v1/inquiries?cursor=...&status=ALL` | 필터 탭 → 재호출 | access |
| 565:41948 | 목록 스크롤 A | 동상 (cursor) | — | access |
| 565:42034 | 목록 스크롤 B | 동상 | — | access |
| 565:42120 | 목록 스크롤 C | 동상 | — | access |
| — | 문의 상세 | `GET /api/v1/inquiries/{id}` | — | access |
| 522:28723 | 답변 미완료 수정 | `GET /api/v1/inquiries/{id}` | `PATCH /api/v1/inquiries/{id}` | access |
| 479:27179 | 수정 변형 | 동상 | — | access |
| — | 삭제 확인 모달 | — | `DELETE /api/v1/inquiries/{id}` | access |
| — | 닫기 버튼 (ANSWERED 상태) | — | `POST /api/v1/inquiries/{id}/close` | access |

**공통**:
- 6 카테고리: `SERVICE_ERROR`/`PLACE_INFO_ERROR`/`ACCOUNT_ISSUE`/`REVIEW_REPORT_REQUEST`/`SUGGESTION`/`ETC` (사용자 확정)
  - ❓ [11-inquiry-spec.md §2.5](./11-inquiry-spec.md) 에서는 `FEATURE_REQUEST` 로 되어 있으나 사용자 확정값 `SUGGESTION` 으로 변경 필요 (Phase 5 에서 최종)
- 본문 1000자 (사용자 확정)
- 첨부 5장 (사용자 확정)
- Rate limit: 분당 3건, 일 30건 (사용자 확정) — `INQUIRY_008` (429)
- PENDING 에서만 수정/삭제 (사용자 확정)

---

## 12. 마이페이지/기타/모달 (2+ 화면)

| Figma ID | 화면명 | API (진입 시) | API (액션 시) | 인증 |
|---|---|---|---|---|
| — | 마이페이지 메인 | `GET /api/v1/members/me` + `GET /api/v1/pets` + `GET /api/v1/notifications/unread-count` | — | access |
| — | 프로필 수정 | `GET /api/v1/members/me` | `PATCH /api/v1/members/me` (기존 구현) + Presigned 업로드 (프로필 이미지) | access |
| — | 로그아웃 | — | `POST /api/v1/auth/logout` + `DELETE /api/v1/notifications/devices/{token}` | access |
| — | 탈퇴 확인 모달 | — | `POST /api/v1/member/withdraw` | access |
| — | 공통 에러 모달 | — | — | - |
| — | 공통 네트워크 에러 재시도 | — | 재호출 | - |

---

## A. 화면 진입 시 공통 사이드이펙트

### A.1 앱 실행 시 (1회)
1. `GET /api/v1/meta/splash` — 버전/유지보수/정책 URL
2. 로그인 상태면 `GET /api/v1/auth/session` — 세션 유효성 확인
3. 로그인 상태면 `POST /api/v1/notifications/devices` — 토큰 UPSERT
4. 로그인 상태 + 재동의 약관 있으면 `pendingTermAgreements` 처리

### A.2 백그라운드 → 포그라운드 복귀 시
- `GET /api/v1/auth/session` — 세션 만료 시 `POST /api/v1/auth/reissue`
- `GET /api/v1/notifications/unread-count` — 배지 갱신

### A.3 업체 상세 진입 시 (로그인)
- `POST /api/v1/members/me/recently-viewed { placeId }` (자동, 비동기)

### A.4 세션 만료 시
- `POST /api/v1/auth/reissue` (refreshToken 으로)
- 실패 시 로그인 화면으로 복귀

---

## B. 화면 유형별 API 호출 패턴

| 화면 유형 | 진입 호출 | 페이지네이션 | 재호출 트리거 |
|---|---|---|---|
| 리스트 화면 (목록) | `GET /...?cursor=&size=20` | Cursor (공통 정책 §4.1) | 당겨서 새로고침, 무한스크롤, 필터 변경 |
| 상세 화면 | `GET /.../{id}` | - | - |
| 작성/수정 화면 | 수정 시 `GET /.../{id}` | - | 제출 시 `POST`/`PATCH` |
| 모달/바텀시트 | 별도 API 없음 | - | 상위 화면 상태 반영 |
| 지도 화면 | `GET /places?lat=...&lng=...&sort=distance` | 좌표 바운딩 기반 | 뷰포트 변경 (선택, Phase 3+) |
| 필터 적용 화면 | 쿼리 파라미터 재호출 | Cursor | 필터 변경 |

---

## C. 누락 가능성 / Phase 5 에서 확정 필요

### C.1 기획서에 없지만 화면에서 필요해 보이는 API
- **업체 신고** (`489:56526`) — `POST /api/v1/places/{placeId}/report` — Report 도메인 통합 시 최종 경로 확정 필요 ([03-common-policies.md §3.2](./03-common-policies.md) 에서 개괄만 다룸)
- **프로필 이미지 업로드** (마이페이지) — 회원 프로필 이미지 업로드 엔드포인트 미정의 (Pet/Review 업로드와 동일 패턴 예상)
- **스플래시 메타 통합 엔드포인트** `GET /api/v1/meta/splash` — 기존 구현은 `GET /api/v1/meta/**` 로 분산. 단일 통합 API 필요성 확인
- **공지 알림 권한 요청 후 구독 확인** — OS 네이티브 이후 서버 등록 경로
- **자동완성 / 최근 검색어** — MVP 범위 밖 ([08-search-filter-spec.md §3.7](./08-search-filter-spec.md))
- **업체 뷰포트 기반 재검색** — Phase 3+ ([05-place-spec.md §8.4](./05-place-spec.md))

### C.2 화면 ID 미식별 (Phase 5 진입 전 Figma 재확인)
- 탈퇴 요청 화면 (마이페이지 → 설정 → 탈퇴)
- 로그아웃 모달
- 공통 네트워크 에러/토스트
- 프로필 이미지 편집 화면
- 리뷰 신고 모달 (node id 명시 안 됨)

### C.3 사용자 확정 vs 기획서 간 불일치 정리
| 항목 | 기획서 값 | 사용자 확정 값 | 조치 |
|---|---|---|---|
| 편의시설 필터 연산 | AND ([08 §4.3](./08-search-filter-spec.md)) | **OR** | Phase 5 에서 OR 로 최종 반영 |
| 신고 자동 HIDDEN 임계값 | 3건 ([06 §4.1-10](./06-review-spec.md)) | **5건** | Phase 5 에서 5건 반영 |
| 문의 카테고리 FEATURE_REQUEST | [11 §2.5](./11-inquiry-spec.md) | **SUGGESTION** | Phase 5 Enum 변경 |
| 리뷰 연결 Pet 최대 | 5마리 제안 ([06 §4.1-5](./06-review-spec.md)) | **5마리** | 일치 |
| Pet 최대 등록 수 | 10마리 제안 ([04 §4.2](./04-pet-spec.md)) | **10마리** | 일치 |
| 최근 본 장소 최대 | 50개 제안 ([07 §4.1-4](./07-wishlist-spec.md)) | **50개 LRU** | 일치 |
| 트렌딩 윈도우 | 7d 제안 ([07 §4.2](./07-wishlist-spec.md)) | **7d, fallback 빈 배열** | 일치 |
| 알림 4 타입 | 4 타입 ([09 §2.5](./09-notification-spec.md)) | **4 타입** | 일치 |

---

## D. 인증 매트릭스 요약

| 기능 영역 | 비로그인 | 온보딩 중 | 로그인 (ACTIVE+COMPLETED) |
|---|:---:|:---:|:---:|
| 스플래시/메타 | O | O | O |
| 소셜 로그인 | O | - | - |
| 본인인증 | - | O (`onboardingToken`) | - |
| 프로필 완성 | - | O (`onboardingAccessToken`) | - |
| 홈/검색/업체 상세 조회 | O | X (온보딩 완료 전 차단) | O |
| 맞춤리뷰 (`sort=tailored`) | X | X | O (대표 Pet 필수) |
| 리뷰 작성/찜/최근본 | X | X | O |
| Pet 관리 | - | - | O |
| 저장 필터 | - | - | O |
| 알림/설정 | - | - | O |
| 문의 | - | - | O |
| 약관 조회 | O | O | O |
| 약관 동의/철회 | - | O (온보딩) | O (재동의) |
| 탈퇴 | - | - | O |

---

## E. 관련 문서

- [00. 서비스 개요](./00-overview.md)
- [01. 도메인 맵](./01-domain-map.md)
- [03. 공통 정책](./03-common-policies.md)
- [12. UX 플로우](./12-ux-flows.md) — 플로우 단위 상태 전이
- 도메인별 상세: [04. Pet](./04-pet-spec.md) · [05. Place](./05-place-spec.md) · [06. Review](./06-review-spec.md) · [07. Wishlist](./07-wishlist-spec.md) · [08. Search](./08-search-filter-spec.md) · [09. Notification](./09-notification-spec.md) · [10. Announcement](./10-announcement-spec.md) · [11. Inquiry](./11-inquiry-spec.md)
- `.claude/CLAUDE.md` — 기존 구현 인증 플로우
