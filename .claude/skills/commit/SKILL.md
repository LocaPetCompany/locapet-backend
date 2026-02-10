---
name: commit
description: git diff를 분석하여 프로젝트 컨벤션에 맞는 커밋 메시지 생성. staged 변경사항이 있을 때 사용.
model: haiku
---

# 커밋 메시지 생성

## 사용법

- `/commit` - 커밋 메시지 생성
- `/commit JIRA-1234` - 티켓 번호 포함하여 생성

## 지시사항

1. `git status`로 변경사항 확인
2. **변경사항이 없으면 "커밋할 변경사항이 없습니다" 안내 후 종료**
3. staged 변경사항이 없고 unstaged 변경사항이 있으면 `git add .`로 staging
4. `git diff --staged`로 변경 내용 분석
5. 프로젝트 컨벤션에 맞는 커밋 메시지 **제안 및 사용자 확인**
6. 확인 후 커밋 생성 (HEREDOC 사용, Co-Authored-By 제외)

## 커밋 타입

| 타입 | 설명 |
|------|------|
| feat | 새 기능 추가 |
| fix | 버그 수정 |
| refactor | 코드 리팩토링 (기능 변경 없음) |
| docs | 문서 수정 |
| test | 테스트 추가/수정 |
| chore | 빌드, 설정, 의존성 변경 |
| style | 코드 포맷팅, 세미콜론 등 (기능 변경 없음) |
| perf | 성능 개선 |
| ci | CI/CD 설정 변경 |

## 형식
```
type(scope): 작업 요약

- 작업 상세 1
- 작업 상세 2

Related to: 티켓번호
```

### 구성 요소

| 요소 | 필수 | 설명 |
|------|------|------|
| type | ✅ | 커밋 타입 |
| scope | ❌ | 변경 영역 (모듈명, 기능명) |
| ! | ❌ | Breaking change 표시 (`feat!:`) |
| 작업 요약 | ✅ | 50자 이내, 현재형 |
| 작업 상세 | ❌ | 변경사항이 복잡할 때 |
| Related to | ❌ | 티켓 번호 (인자로 전달 시) |

### Scope 예시

멀티모듈 프로젝트의 경우:
- `feat(app-api):` - app-api 모듈 변경
- `fix(domain):` - domain 모듈 변경
- `refactor(common):` - common 모듈 변경

단일 모듈이거나 전역 변경:
- `chore:` - scope 생략

## 규칙

- 제목은 **50자 이내**, 본문은 **72자 줄바꿈**
- **현재형** 사용 (추가, 수정, 변경 ✅ / 추가함, 수정했음 ❌)
- 제목 끝에 **마침표 없음**
- 제목과 본문 사이 **빈 줄 필수**
- 여러 변경사항은 가장 중요한 것 위주로

## 예시

**기본 (scope 포함):**
```
feat(app-api): 사용자 인증 토큰 캐싱 추가

- Redis 기반 토큰 캐시 구현
- TTL 1시간 설정

Related to: JIRA-1234
```

**Breaking Change:**
```
feat(domain)!: User 엔티티 필드명 변경

- username → loginId로 변경
- 기존 API 응답 호환성 깨짐

Related to: JIRA-5678
```

**간단한 수정 (scope, 본문 생략):**
```
fix: Tiqets 취소 정책 penaltyRate 계산 오류 수정
```

**피해야 할 예시:**

| ❌ 피해야 할 것 | ✅ 좋은 예시 |
|----------------|-------------|
| `수정함` | `fix: null 체크 누락으로 인한 NPE 수정` |
| `feat: Add feature` | `feat: 기능 추가` (한국어 사용) |
| `여러 버그 수정` | 커밋을 분리하거나 주요 수정 명시 |