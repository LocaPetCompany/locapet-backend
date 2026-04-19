# 01. 도메인 맵 (Domain Map)

> 본 문서는 로카펫의 **12개 기능 도메인**을 정리하고, 도메인 간 관계와 핵심 엔티티를 백엔드 관점에서 도식화한다.
> Figma 125개 화면을 분석하여 도출한 도메인 경계를 정의하며, 이후 API·DB 설계의 기반이 된다.

---

## 1. 도메인 목록 (한눈에 보기)

| # | 도메인 | 책임 한 줄 | MVP | 구현 상태 |
|---|--------|-----------|:---:|:---:|
| D1 | **인증/온보딩** (Auth) | 소셜 로그인, 본인인증, 프로필 완성, 탈퇴 |  O  | 완료 |
| D2 | **메타** (Meta) | 앱 버전, 유지보수, 공지사항, FAQ |  O  | 일부 완료 (FAQ 미구현) |
| D3 | **회원/마이페이지** (Member) | 프로필 조회·수정, 계정 상태 관리 |  O  | 기본 완료 |
| D4 | **반려동물 관리** (Pet) | 반려동물 프로필 등록·수정·대표 설정 |  O  | 미구현 |
| D5 | **업체/상세** (Place) | 장소 정보, 동반 조건, 카테고리 |  O  | 미구현 **핵심 중심축** |
| D6 | **홈/피드** (Feed) | 추천·큐레이션 섹션 구성 |  O  | 미구현 |
| D7 | **검색/필터** (Search) | 키워드·조건 검색, 저장된 필터 | 부분 | 미구현 |
| D8 | **지도/위치** (Map) | 지도·내 주변 탐색, 좌표 검색 |  O  | 미구현 |
| D9 | **리뷰** (Review) | 리뷰 작성·조회·수정·삭제, 맞춤리뷰 |  O  | 미구현 |
| D10 | **찜/저장** (Favorite) | 찜, 최근 본 장소 | 부분 | 미구현 |
| D11 | **알림/공지** (Notification) | 공지사항, 알림 설정, 푸시 | 부분 | 공지만 완료 |
| D12 | **고객지원/문의** (Support) | 1:1 문의, 신고하기 |  O  | 미구현 |

---

## 2. 핵심 중심축: **Place** 도메인

```
                                  ┌──────────────┐
                                  │  Member (D3) │
                                  └──────┬───────┘
                                         │ owns
                                         ▼
                                  ┌──────────────┐
                                  │   Pet (D4)   │
                                  └──────┬───────┘
                                         │ linkedTo
                                         ▼
 ┌─────────┐   shows    ┌─────────┐    about    ┌──────────┐   writes
 │Feed(D6) │───────────▶│         │◀───────────│          │◀─────────┐
 └─────────┘            │         │             │          │          │
                        │         │             │          │          │
 ┌─────────┐  searches  │  Place  │    for      │  Review  │          │
 │Search   │───────────▶│  (D5)   │◀───────────│  (D9)    │──────────┤
 │(D7)     │            │         │             │          │  authored│
 └─────────┘            │         │             │          │    by    │
                        │         │             │          │          │
 ┌─────────┐  locates   │         │    reports  │          │        Member
 │Map (D8) │───────────▶│         │◀───────────│          │
 └─────────┘            └─────┬───┘             └──────────┘
                              │
                              │ favorited by Member
                              ▼
                        ┌──────────────┐
                        │Favorite (D10)│
                        └──────────────┘
```

- **Place는 D5~D10 거의 모든 기능의 주어(subject)** 다.
- Member·Pet·Place·Review 의 4각형이 "맞춤리뷰" 의 논리적 기반이다.
- Meta(D2) / Notification(D11) / Support(D12) / Auth(D1) 은 Place와 직접 연결되지 않는 **횡단(cross-cutting)** 도메인.

---

## 3. 도메인 상세

### D1. 인증/온보딩 (Auth)

| 항목 | 내용 |
|------|------|
| 책임 | 소셜 로그인, 본인인증(PASS), 온보딩 세션 관리, 탈퇴/재가입 |
| 주요 엔티티 | `Member`, `SocialAccount`, `IdentityLock`, `IdentityVerification`, (Redis) `OnboardingSession` |
| 외부 의존 | Member(D3) |
| MVP | O (구현 완료) |
| Figma 화면 수 | 11 |

### D2. 메타 (Meta)

| 항목 | 내용 |
|------|------|
| 책임 | 앱 버전, 유지보수, 공지사항, FAQ, 약관/정책 URL |
| 주요 엔티티 | `AppVersion`, `Maintenance`, `Notice`, `FAQ`(신규) |
| 외부 의존 | 없음 (독립) |
| MVP | O (공지·버전·유지보수는 구현 완료, **FAQ·약관 상세페이지는 미구현**) |
| Figma 화면 수 | 공지/FAQ 관련 9 |

### D3. 회원/마이페이지 (Member)

| 항목 | 내용 |
|------|------|
| 책임 | 프로필 조회·수정, 닉네임 중복 검증, 로그아웃, 계정 상태 2축 관리 |
| 주요 엔티티 | `Member` (D1과 공유) |
| 외부 의존 | Auth(D1) |
| MVP | O (기본 완료, 프로필 이미지 업로드는 미구현) |
| Figma 화면 수 | 8 |

### D4. 반려동물 관리 (Pet)

| 항목 | 내용 |
|------|------|
| 책임 | 반려동물 등록(10단계 플로우), 수정, 삭제, 대표 반려동물 설정 |
| 주요 엔티티 | `Pet` (신규) |
| Pet 주요 속성 (추정) | `memberId`, `name`, `species`(DOG/CAT), `breed`, `birthDate`, `weightKg`, `gender`, `neutered`, `profileImageUrl`, `isRepresentative`, `personality`(기질/성격 태그) ❓, `size`(소형/중형/대형) ❓ |
| 외부 의존 | Member(D3) |
| MVP | O |
| Figma 화면 수 | 21 (가장 많은 입력 플로우) |
| 비고 | **맞춤리뷰의 기준 데이터**가 되므로 속성 확정이 중요 |

### D5. 업체/상세 (Place) **★ 중심 도메인**

| 항목 | 내용 |
|------|------|
| 책임 | 장소 마스터 데이터, 카테고리, 반려동물 동반 조건, 운영시간, 사진, 위치 좌표 |
| 주요 엔티티 | `Place`, `PlaceCategory`, `PlacePetPolicy`(동반 정책), `PlaceImage`, `PlaceBusinessHours` |
| Place 주요 속성 (추정) | `id`, `name`, `categoryId`, `address`, `latitude`, `longitude`, `phone`, `kakaoChannelId`, `email`, `description`, `status`(PUBLISHED/HIDDEN), `representativeImageUrl` |
| Pet Policy 속성 (추정) | `allowedSpecies`(DOG/CAT/BOTH), `allowedSizes`(SMALL/MEDIUM/LARGE), `indoorAllowed`, `cageRequired`, `extraFee`, `notes` |
| 외부 의존 | 없음 (마스터 데이터) |
| MVP | O |
| Figma 화면 수 | 3 (상세/스크롤/리뷰 섹션) |
| 카테고리 (추정) | 식당, 카페, 유치원(로카유치원), 숙소, 공원/산책로 ❓ — 확정 필요 |

### D6. 홈/피드 (Feed)

| 항목 | 내용 |
|------|------|
| 책임 | 홈 화면 섹션 구성 (추천 장소, 요즘 찜 많은 장소, 로카 추천 장소) |
| 주요 엔티티 | `FeedSection` (관리자가 구성, 신규) 또는 런타임 집계 ❓ |
| 외부 의존 | Place(D5), Favorite(D10), Review(D9) |
| MVP | O (섹션은 관리자 수동 큐레이션으로 시작 추천) |
| Figma 화면 수 | 4 |

### D7. 검색/필터 (Search)

| 항목 | 내용 |
|------|------|
| 책임 | 키워드 검색, 카테고리/지역/동반 조건 필터, **저장된 필터** |
| 주요 엔티티 | `SavedFilter` (신규), `SearchHistory`(선택) |
| SavedFilter 속성 (추정) | `memberId`, `name`, `filterCriteria`(JSONB), `createdAt` |
| 외부 의존 | Place(D5), Member(D3) |
| MVP | 기본 검색·주요 필터 O / **저장된 필터는 Phase 2 검토** (22개 화면으로 복잡도 큼) |
| Figma 화면 수 | 22 |

### D8. 지도/위치 (Map)

| 항목 | 내용 |
|------|------|
| 책임 | 지도 표시, 좌표 기반 주변 검색, 지도↔목록 전환 |
| 주요 엔티티 | Place(D5)의 좌표 활용. 신규 엔티티 없음 |
| 외부 의존 | Place(D5) |
| 외부 API | 카카오맵 / 네이버지도 / Google Maps ❓ — 선정 필요 |
| MVP | O |
| Figma 화면 수 | 7 |
| 기술 검토 | PostGIS 또는 Redis Geo 도입 ❓ |

### D9. 리뷰 (Review) **★ 핵심 가치**

| 항목 | 내용 |
|------|------|
| 책임 | 리뷰 CRUD, 사진 업로드, 평점, **맞춤리뷰(Pet 연동)** |
| 주요 엔티티 | `Review`, `ReviewImage`, `ReviewPet`(리뷰↔반려동물 N:M), `ReviewReport`(신고) |
| Review 주요 속성 | `memberId`, `placeId`, `rating`(1~5), `content`, `visitedAt`, `status`(ACTIVE/HIDDEN/DELETED) |
| ReviewPet 속성 | `reviewId`, `petId`, `petSnapshotJson`(리뷰 시점 반려동물 정보 스냅샷) |
| 외부 의존 | Member(D3), Pet(D4), Place(D5) |
| MVP | O |
| Figma 화면 수 | 15 |
| 핵심 로직 | **맞춤리뷰 필터링**: 조회 유저의 대표 반려동물 속성과 리뷰의 Pet 스냅샷 속성을 매칭 (동종+유사 크기 기준) |

### D10. 찜/저장 (Favorite)

| 항목 | 내용 |
|------|------|
| 책임 | 찜(즐겨찾기), 최근 본 장소 |
| 주요 엔티티 | `Favorite`, `RecentlyViewedPlace`(선택) |
| Favorite 속성 | `memberId`, `placeId`, `createdAt` (unique: memberId+placeId) |
| 외부 의존 | Member(D3), Place(D5) |
| MVP | 찜 O / **최근 본 장소는 Phase 2 검토** (클라이언트 로컬 저장으로도 대체 가능) |
| Figma 화면 수 | 5 |

### D11. 알림/공지 (Notification)

| 항목 | 내용 |
|------|------|
| 책임 | 공지사항 노출, 알림 설정, 푸시 알림 발송 |
| 주요 엔티티 | `Notice`(구현), `NotificationSetting`(신규), `PushToken`(신규), `NotificationHistory`(신규) |
| 외부 의존 | Member(D3), FCM/APNs ❓ |
| MVP | 공지 O / **푸시 알림은 Phase 2** |
| Figma 화면 수 | 9 |

### D12. 고객지원/문의 (Support)

| 항목 | 내용 |
|------|------|
| 책임 | 1:1 문의 작성·조회, 신고(리뷰/업체) |
| 주요 엔티티 | `Inquiry`, `InquiryCategory`, `InquiryAnswer`, `Report`(통합 신고) |
| Inquiry 속성 | `memberId`, `categoryId`, `title`, `content`, `status`(PENDING/ANSWERED/CLOSED), `createdAt` |
| 외부 의존 | Member(D3), Review(D9)·Place(D5) — Report는 다형적 연결 |
| MVP | O |
| Figma 화면 수 | 문의 11 + 신고/팝업 일부 |

---

## 4. 관계 요약 (ER 수준 개요)

```
Member (1) ─── (N) Pet
Member (1) ─── (N) SocialAccount
Member (1) ─── (N) Favorite ─── (N:1) Place
Member (1) ─── (N) Review ─── (N:1) Place
Member (1) ─── (N) SavedFilter
Member (1) ─── (N) Inquiry
Review (1) ─── (N) ReviewImage
Review (N) ─── (M) Pet  (via ReviewPet + snapshot)
Place (1) ─── (N) PlaceImage
Place (1) ─── (1) PlacePetPolicy
Place (1) ─── (N) PlaceBusinessHours
Notice / Maintenance / AppVersion : 독립 (관리자 CRUD)
```

---

## 5. 도메인 간 경계 원칙

1. **Place는 읽기 중심** — 업체 정보는 관리자만 CUD, 일반 유저는 R만. 캐시 적극 활용.
2. **Member · Pet 은 유저 소유 데이터** — 탈퇴 시 처리 정책 필요 (익명화 vs 삭제). [03-common-policies.md](./03-common-policies.md) 참조.
3. **Review는 독립적 작성·수정** — Pet 정보를 스냅샷으로 저장해 Pet 변경/삭제에 내성 확보.
4. **Favorite는 단순 교차 엔티티** — 탈퇴 시 삭제 가능.
5. **Search/Map은 Place의 뷰(view) 계층** — 별도 엔티티 최소화, 검색 인덱스로 처리.

---

## 6. MVP 우선순위 (구축 순서 제안)

| Phase | 도메인 | 이유 |
|-------|--------|------|
| Phase 0 (완료) | D1 Auth, D2 Meta (부분), D3 Member | 진입 가능해야 함 |
| Phase 1 | **D5 Place**, **D4 Pet** | 모든 후속 도메인의 기반 |
| Phase 2 | D9 Review (맞춤리뷰 포함), D10 Favorite | 핵심 가치 달성 |
| Phase 3 | D8 Map, D7 Search (기본), D6 Feed | 탐색 경로 완성 |
| Phase 4 | D12 Support, D11 Notification(공지 외), D2 FAQ | 운영 안정화 |
| Phase 5 | D7 SavedFilter, D11 Push, D10 RecentlyViewed | 고도화 |

---

## 7. 관련 문서

- [00. 서비스 개요](./00-overview.md)
- [02. 용어집](./02-glossary.md)
- [03. 공통 정책](./03-common-policies.md)
