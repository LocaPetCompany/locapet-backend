# 02. 용어집 (Glossary)

> 본 문서는 로카펫 프로젝트에서 사용하는 **도메인 용어 · 비즈니스 개념 · 기술 용어**를 통일된 정의로 정리한다.
> 원칙: 한글(영문) 표기, 백엔드 엔티티/필드명과 매핑이 있으면 명시, 기존 `CLAUDE.md`와 일관성 유지.

---

## A. 사용자 · 계정

### 회원 (Member)
- 서비스에 가입한 일반 유저. JPA 엔티티 `Member` 로 표현.
- **2축 상태 모델**을 가진다. (아래 용어 참조)
- 역할(`MemberRole`): `USER`, `ADMIN`.

### 계정 상태 (AccountStatus) — 축 1
계정 생명주기를 표현하는 Enum. DB 저장.

| 값 | 의미 |
|---|---|
| `ACTIVE` | 활성 |
| `WITHDRAW_REQUESTED` | 탈퇴 신청됨(30일 유예 중, 복구 가능) |
| `WITHDRAWN` | 탈퇴 완료 (유예 종료 또는 즉시 탈퇴) |
| `FORCE_WITHDRAWN` | 관리자에 의한 강제 탈퇴 |

### 온보딩 단계 (OnboardingStage) — 축 2
유저의 온보딩 진행도를 표현. DB 저장.

| 값 | 의미 |
|---|---|
| `IDENTITY_REQUIRED` | **가상** 상태 (DB 미저장, Redis 온보딩 세션 기반) — 본인인증 전 |
| `PROFILE_REQUIRED` | 본인인증 완료, 프로필(닉네임·약관) 미완료 |
| `COMPLETED` | 온보딩 완료, 정상 이용 가능 |

### 소셜 계정 (SocialAccount)
- 회원과 연결된 소셜 로그인 계정. `SocialProvider` × `providerUserId` 단위로 유니크.
- Provider: `KAKAO`, `NAVER`, `GOOGLE`, `APPLE`.

### 본인인증 (Identity Verification)
- PASS 인증을 통한 실명 + 성별 + 생년월일 + 전화번호 + CI 확인.
- 성공 시 CI를 HMAC-SHA256 해시로 변환하여 `Member.ciHash`에 저장.
- `identity_verifications` 테이블에 감사 로그 기록.

### CI 해시 (CI Hash)
- PASS에서 발급하는 CI(Connecting Information)의 HMAC 해시값.
- 원본 CI는 저장하지 않는다. **중복 가입 방지·계정 잠금의 키** 역할.

### Identity Lock
- CI 해시 기반 가입/재가입 잠금 정책.
- `IdentityLockType`: `ACTIVE_ACCOUNT` (활성 계정 중복 차단), `TEMPORARY` (일시 잠금 — 탈퇴 유예 등), `PERMANENT` (영구 차단).

### 탈퇴 유예 (Withdrawal Grace Period)
- 탈퇴 신청 후 **30일간 복구 가능**한 기간.
- 해당 기간 내 소셜 로그인 시 `WITHDRAW_REQUESTED` → `ACTIVE` 자동 복구.

### 탈퇴 유형 (WithdrawalType)
- `VOLUNTARY` (자발적 탈퇴), `FORCED` (관리자 강제 탈퇴).

### 재가입 대기 (Rejoin Cooldown)
- 탈퇴 완료 후 일정 기간 동일 CI로 재가입 차단. ❓기간 정책 미확정.

### 관리자 (Admin)
- `MemberRole.ADMIN` 또는 별도 admin-api 전용 유저. 업체·리뷰·문의·공지 관리.

---

## B. 토큰 · 세션

### accessToken
- JWT, `type=ACCESS`, TTL 30분. 인증된 API 호출에 사용.

### refreshToken
- Redis 저장, TTL 14일. accessToken 갱신용.

### onboardingToken
- UUID 형식, Redis 저장, TTL 10분, **1회용**. 소셜 로그인(신규) 직후 발급, 본인인증 요청에 사용.

### onboardingAccessToken
- JWT, `type=ONBOARDING`, TTL 30분. 본인인증 성공 후 프로필 완성 요청에 사용.

### 온보딩 세션 (OnboardingSession)
- Redis에 저장되는 온보딩 진행 상태. `onboardingToken` 을 키로 조회.

---

## C. 반려동물 (Pet)

### 반려동물 (Pet)
- 회원이 등록한 자신의 반려동물. 1회원 N반려동물.

### 대표 반려동물 (Representative Pet)
- 한 회원당 1마리만 지정 가능. **홈/검색/추천의 기본 컨텍스트**와 **리뷰 작성 시 기본 선택값**이 됨.
- 필드: `Pet.isRepresentative: Boolean`.

### 반려동물 종 (Species)
- `DOG`(개), `CAT`(고양이). ❓추후 확장 가능성(소동물 등)은 MVP 제외.

### 반려동물 크기 (Size) ❓
- `SMALL`(소형, ~10kg), `MEDIUM`(중형, ~20kg), `LARGE`(대형, 20kg+) — 제안값. 기준 확정 필요.

### 반려동물 기질/성격 (Personality) ❓
- Figma의 10단계 등록 플로우에서 입력하는 태그성 속성으로 추정. 종류 확정 필요.

---

## D. 업체 (Place)

### 업체 (Place)
- 반려동물 동반 가능한 장소. 식당/카페/유치원 등 카테고리 구분.

### 업체 카테고리 (PlaceCategory)
- 1차 분류. 추정값: 식당(Restaurant), 카페(Cafe), **로카유치원**(Daycare), 숙소(Accommodation), 공원/산책로(Park) ❓.
- 카테고리 트리 구조 여부 미결. (예: 식당 > 한식 > 분식) ❓

### 동반 정책 (Pet Policy)
- 각 업체가 반려동물 동반 시 적용하는 조건.
- 항목 예: 동반 가능 종, 크기 제한, 실내 동반 여부, 케이지 필수 여부, 추가 요금.

### 로카 추천 장소 (Loca Pick)
- 운영진이 큐레이션한 추천 업체. 홈 전용 섹션.

### 로카유치원 (Loca Daycare)
- 반려동물 유치원 카테고리의 브랜드명 섹션. 전용 지도·목록 진입점 제공.

### 요즘 찜 많은 장소 (Trending)
- 최근 N일 찜 등록 수 상위 업체. 집계 주기/기준 ❓ 확정 필요.

---

## E. 검색 · 필터

### 검색 필터 (Search Filter)
- 카테고리·지역·동반 조건 등 검색 시 적용되는 조건 집합.

### 저장된 필터 (Saved Filter)
- 사용자가 자주 쓰는 필터 조합에 이름을 붙여 저장한 것. `SavedFilter` 엔티티.
- 재사용·수정·삭제 가능.

### 필터 적용 (Apply Filter)
- 저장된 필터를 현재 검색에 불러와 적용하는 동작.

---

## F. 지도

### 내 주변 (Nearby)
- 사용자의 현재 위치 기반 반경 내 업체 탐색 기능.

### 지도 보기 / 목록 보기 (Map View / List View)
- 동일 검색 결과의 2가지 표현 방식. 토글 전환.

---

## G. 리뷰

### 리뷰 (Review)
- 업체에 대한 사용자 후기. 평점(1~5) + 본문 + 사진(선택) + 방문한 반려동물 선택.

### 맞춤리뷰 (Personalized Review / Tailored Review)
- 조회자의 **대표 반려동물 속성과 유사한 조건**의 리뷰만 필터링한 결과.
- 매칭 기준 (제안): 동일 종(species) + 유사 크기(size).
- 구현: 리뷰 작성 시 연결된 반려동물의 속성을 **스냅샷(ReviewPet.petSnapshotJson)** 으로 저장해 Pet 변경/삭제에 내성.

### 리뷰 정렬 (Review Sort)
- 최신순, 평점 높은순, 평점 낮은순, 도움순 ❓(도움 기능 도입 여부 미결).

### 리뷰 신고 (Review Report)
- 부적절한 리뷰에 대한 신고. `ReviewReport` 엔티티. 관리자 처리 후 `Review.status` 변경.

### 리뷰 스냅샷 (Review Pet Snapshot)
- 리뷰 작성 시점의 반려동물 정보(종·크기·품종) JSON 기록. Pet 삭제/수정과 독립.

---

## H. 찜 · 저장

### 찜 (Favorite)
- 업체를 즐겨찾기로 추가하는 동작 및 엔티티. 1회원 × 1업체 유니크.

### 최근 본 장소 (Recently Viewed Place) — Phase 2 검토
- 사용자가 상세 페이지를 조회한 업체 이력. 최대 N개, TTL 기반. ❓구현 위치(서버/클라) 결정 필요.

---

## I. 알림 · 공지

### 공지사항 (Notice)
- 앱 내 노출되는 운영 공지. `Notice` 엔티티. `noticeType`: `INFO` / `WARNING` / `URGENT`.

### 앱 버전 (AppVersion)
- 플랫폼별 앱 최신 버전 정보. `forceUpdate`, `minimumVersion` 포함.

### 유지보수 (Maintenance)
- 서비스 점검 안내. `startTime`, `endTime` 구간 중 앱 진입 차단/경고.

### 알림 설정 (Notification Setting) ❓
- 사용자별 푸시 카테고리 on/off. Phase 2.

### 자주 묻는 질문 (FAQ) ❓
- 카테고리별 Q&A 목록. `FAQ` 엔티티 신규. Meta 도메인 소속.

---

## J. 고객지원 · 문의

### 1:1 문의 (Inquiry)
- 회원이 운영진에게 보내는 개인 문의. 유형별 카테고리 분류.

### 문의 카테고리 (InquiryCategory)
- 서비스 오류, 업체 정보 오류, 계정 문제, 기타 등. ❓ 확정 필요.

### 문의 답변 (InquiryAnswer)
- 관리자가 작성한 문의에 대한 응답.

### 신고 (Report)
- 리뷰·업체 등에 대한 부적절 제보. 대상 타입 + 대상 ID + 사유.

---

## K. 공통 · 기술

### UTC 타임존
- 모든 시각은 `Instant` 기반 UTC 저장·전송. `LocalDateTime` 사용 금지. (예외: 생년월일 `LocalDate`)

### ISO-8601
- API에서 주고받는 시각 문자열 포맷. 예: `2026-04-18T09:30:00Z`.

### Flyway
- DB 스키마 마이그레이션 도구. `V{번호}__{설명}.sql` 파일.

### 모듈러 모놀리스 (Modular Monolith)
- `app-api` + `admin-api` + `domain` + `common` 의 4모듈 Gradle 구조.

### CLAUDE.md
- 프로젝트 루트의 `.claude/CLAUDE.md`. 개발자·AI 에이전트용 컨텍스트 문서.

### 스플래시 메타 (Splash Meta)
- 앱 진입 시 1회 조회하는 통합 메타 (버전·공지·유지보수·정책 URL).

---

## L. 용어 정합성 주의사항

1. **리뷰 vs 후기**: 공식 용어는 "리뷰"로 통일.
2. **업체 vs 장소 vs 가게**: 공식 용어는 "업체"(UI 노출은 "장소"도 허용). 엔티티명은 `Place`.
3. **반려동물 vs 펫**: UI는 "반려동물", 엔티티/필드는 `Pet` / `pet`.
4. **찜 vs 즐겨찾기**: UI·코드 모두 "찜" / `Favorite`.
5. **맞춤리뷰 vs 추천리뷰**: "맞춤리뷰"로 통일 (추천은 큐레이션 영역과 혼동 방지).
6. **로카 vs Loca**: 서비스명 표기는 "로카펫 / Locapet", 내부 큐레이션 브랜드는 "로카 추천" / "로카유치원".

---

## 관련 문서

- [00. 서비스 개요](./00-overview.md)
- [01. 도메인 맵](./01-domain-map.md)
- [03. 공통 정책](./03-common-policies.md)
