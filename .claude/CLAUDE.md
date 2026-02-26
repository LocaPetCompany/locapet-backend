# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Locapet Backend - A multi-module Kotlin/Spring Boot backend service for the Locapet application. The project uses Gradle with Kotlin DSL for build management.

**Technology Stack:**
- Kotlin 2.2.21
- Spring Boot 4.0.1
- JDK 21
- PostgreSQL 16 database
- Redis cache
- JPA/Hibernate
- Flyway (`flyway-core` + `flyway-database-postgresql`) for schema migrations
- Testcontainers for integration testing
- SpringDoc OpenAPI 2.3.0 (Swagger UI)

## Architecture

**Architecture Pattern:** Modular Monolith (모듈러 모놀리스)

This is a **multi-module Gradle project** with the following structure:

### Module Structure

```
locapet-backend/
├── app-api/          # User-facing API (port 8080)
├── admin-api/        # Admin/management API (port 8081)
├── domain/           # Domain entities and JPA repositories (library module)
└── common/           # Shared utilities and common code (library module)
```

### Module Dependencies

- **app-api**: Depends on `domain` and `common`. Full Spring Boot application.
- **admin-api**: Depends on `domain` and `common`. Full Spring Boot application.
- **domain**: Library module (bootJar disabled). Contains JPA entities and data access layer.
- **common**: Library module (bootJar disabled). Contains shared utilities.

Both API modules:
- Are configured with Spring Boot, JPA, and Spring plugins
- Share the same PostgreSQL database (`locapet`)
- Use Redis for caching
- Scan all packages under `com.vivire.locapet` via `@SpringBootApplication(scanBasePackages = ["com.vivire.locapet"])`

## Build Commands

**Note:** This project uses IntelliJ IDEA as the primary IDE. Use the IDE's run configurations when available.

### Build & Run

```bash
# Build all modules
./gradlew build

# Build specific module
./gradlew :app-api:build
./gradlew :admin-api:build

# Run app-api (port 8080)
./gradlew :app-api:bootRun

# Run admin-api (port 8081)
./gradlew :admin-api:bootRun

# Clean build
./gradlew clean build
```

**IntelliJ Run Configurations:**
- `AppApiApplication` - Run the app API server
- `AdminApiApplication` - Run the admin API server

### Testing

**Integration Tests:**
- Tests use **Testcontainers** for PostgreSQL and Redis
- Base class: `IntegrationTestSupport` (note: typo in filename `IntergrationTestSupport.kt`)
- Docker must be running for tests to execute
- Tests automatically start PostgreSQL 16-alpine and Redis containers

```bash
# Run all tests (requires Docker)
./gradlew test

# Run tests for specific module
./gradlew :app-api:test
./gradlew :admin-api:test
./gradlew :domain:test

# Run single test class
./gradlew :app-api:test --tests "com.vivire.locapet.app.support.InfrastructureTest"

# Run specific test method
./gradlew :app-api:test --tests "com.vivire.locapet.app.support.InfrastructureTest"

# Run with info logging
./gradlew test --info
```

### Other Commands

```bash
# Check dependencies
./gradlew dependencies

# List all tasks
./gradlew tasks

# Compile Kotlin
./gradlew compileKotlin
```

## Development Requirements

### Local Services

Both API modules require:
- **PostgreSQL** running on `localhost:5432`
  - Database: `locapet`
  - Username: `root`
  - Password: `password`
- **Redis** running on `localhost:6379`
- **Docker** (required for integration tests with Testcontainers)

### Docker Compose (로컬 개발 환경)

프로젝트 루트의 `docker-compose.yml`로 필요한 모든 서비스를 한 번에 실행할 수 있습니다.

```bash
# 모든 서비스 시작 (PostgreSQL + Redis)
docker-compose up -d

# 서비스 상태 확인
docker-compose ps

# 서비스 종료
docker-compose down

# 데이터 포함 완전 삭제
docker-compose down -v
```

컨테이너 정보:
- `locapet-postgres` - postgres:16-alpine, port 5432
- `locapet-redis` - redis:7-alpine, port 6379

### Configuration Files

- `app-api/src/main/resources/application.yml` - App API configuration
- `admin-api/src/main/resources/application.yml` - Admin API configuration

### API Documentation

**Swagger UI Access:**
- App API: `http://localhost:8080/api-app.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

Configuration:
- APIs sorted alphabetically
- Request duration displayed
- Actuator endpoints included

## Package Structure

All modules follow the base package structure: `com.vivire.locapet.*`

**Application Classes:**
- `com.vivire.locapet.app.AppApiApplication` (app-api module)
- `com.vivire.locapet.admin.AdminApiApplication` (admin-api module)

**Module Structure:**
- `app-api/`: Controllers, services, DTOs for user-facing API
  - `api/` - REST controllers organized by domain (auth, onboarding, member, meta)
  - `global/auth/` - JWT, social login clients, onboarding session, identity verification
  - `global/config/` - Spring configuration classes (CacheConfig, SwaggerConfig)
  - `global/security/` - Spring Security configuration and JWT filter
  - `global/exception/` - Global exception handler and error types
- `domain/`: JPA entities and repositories
  - `member/` - Member, SocialAccount, IdentityLock, IdentityVerification entities + repositories
  - `meta/` - AppVersion, Maintenance, Notice entities + repositories
- `common/`: Shared utilities and configurations
  - `config/` - AuthConfigProperties, MetaConfigProperties, UtcTimeZoneConfig

## Auth & Onboarding Flow

### Member State Model (2축)

회원 상태는 **두 개의 독립 축**으로 관리됩니다:

| 축 | Enum | 값 |
|---|------|---|
| 계정 생명주기 | `AccountStatus` | `ACTIVE`, `WITHDRAW_REQUESTED`, `WITHDRAWN`, `FORCE_WITHDRAWN` |
| 온보딩 진행 | `OnboardingStage` | `IDENTITY_REQUIRED`(가상), `PROFILE_REQUIRED`, `COMPLETED` |

> `IDENTITY_REQUIRED`는 DB에 저장되지 않는 가상 상태. Redis 온보딩 세션으로만 존재.

### 인증 플로우

```
1. POST /api/v1/auth/social/{provider} → 소셜 토큰 검증
     ├─ 기존 ACTIVE+COMPLETED → accessToken + refreshToken 반환
     ├─ 기존 ACTIVE+PROFILE_REQUIRED → onboardingAccessToken 반환
     ├─ WITHDRAW_REQUESTED → 자동 취소 → accessToken + refreshToken 반환
     └─ 신규 → Redis OnboardingSession 생성 → onboardingToken 반환

2. POST /api/v1/onboarding/identity/verify → PASS 본인인증 + CI 검증
     → CI hash 계산 → identity_locks 테이블 확인 → Member + SocialAccount 생성

3. POST /api/v1/onboarding/profile/complete → 닉네임 + 약관 동의
     → OnboardingStage=COMPLETED → accessToken + refreshToken 반환
```

### API 엔드포인트 (app-api)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/auth/social/{provider}` | None | 소셜 로그인 (기존/신규 분기) |
| POST | `/api/v1/auth/reissue` | None | 토큰 재발급 + 상태 반환 |
| GET | `/api/v1/auth/session` | Access token | 세션 상태 조회 (라우팅용) |
| POST | `/api/v1/auth/logout` | Access token | 로그아웃 |
| POST | `/api/v1/onboarding/identity/verify` | None (onboardingToken in body) | PASS 본인인증 |
| POST | `/api/v1/onboarding/profile/complete` | None (onboardingAccessToken in body) | 프로필 완성 |
| POST | `/api/v1/member/withdraw` | Access token | 탈퇴 신청 (30일 유예) |
| GET | `/api/v1/meta/**` | None | 앱 버전, 유지보수, 공지 |

### 토큰 체계

| 토큰 | 저장소 | 용도 | 발급 시점 |
|---|---|---|---|
| `onboardingToken` | Redis (UUID, TTL 10분, 1회용) | 본인인증 요청 | 소셜 로그인 (신규) |
| `onboardingAccessToken` | JWT (type=ONBOARDING, 30분) | 프로필 완성 요청 | 본인인증 성공 |
| `accessToken` | JWT (type=ACCESS, 30분) | 인증된 API 호출 | 온보딩 완료 / 기존 ACTIVE 로그인 |
| `refreshToken` | Redis (14일) | accessToken 갱신 | accessToken과 함께 발급 |

## Database Management

**Schema Management:**
- JPA uses `ddl-auto: validate` - Hibernate validates schema but doesn't modify it
- SQL migrations are located in `domain/src/main/resources/db/migration/`
- Flyway runs automatically on application startup (`flyway.enabled: true`, `flyway.baseline-on-migrate: true`)
- Migration files follow Flyway naming: `V001__create_meta_tables.sql`
- Schema changes must be done via migration files, not JPA auto-DDL
- SQL logging is enabled for development (`show-sql: true`, `format_sql: true`)

**Current Tables:**
- `app_versions` - App version management with force update support
- `maintenances` - Scheduled maintenance notifications
- `notices` - In-app notices with display scheduling
- `members` - 회원 (2축 상태: `account_status` + `onboarding_stage`). V004에서 재구축
- `social_accounts` - 소셜 로그인 연동 (provider + provider_user_id). V004 추가
- `identity_locks` - CI 해시 기반 잠금 정책 (ACTIVE_ACCOUNT / TEMPORARY / PERMANENT). V004 추가
- `identity_verifications` - 본인인증 감사 로그. V004 추가

**PostgreSQL 특이사항:**
- Hibernate Dialect는 별도 설정 없이 자동 감지됨 (`application.yml`에 dialect 설정 없음이 정상)
- Auto-increment: `BIGSERIAL` 타입 사용 (MySQL `AUTO_INCREMENT` 대체), JPA `GenerationType.IDENTITY`와 호환
- 날짜시간: `TIMESTAMPTZ` 사용 (MySQL `DATETIME` 대체, UTC 타임존 포함) - V003 마이그레이션으로 기존 `TIMESTAMP` 컬럼 변환됨
- 인라인 INDEX 불가 — 테이블 정의 외부에 `CREATE INDEX` 별도 선언 필요
- `ENGINE=InnoDB`, `CHARSET=utf8mb4` 구문 불필요 (사용 시 syntax error)

## Important Implementation Details

### Jackson & Redis Serialization

This project uses **Spring Boot 4.0.1** which includes both Jackson 2 and Jackson 3:
- HTTP JSON serialization: Automatic via Spring Boot
- Redis cache serialization: Custom configuration required via `CacheConfig.kt`
- `CacheConfig.kt` configures `GenericJacksonJsonRedisSerializer` (Jackson 3) using `ObjectMapper()`
- Jackson 3 (`tools.jackson.databind.ObjectMapper`) auto-registers all modules on instantiation — `.findAndRegisterModules()` 호출 불필요
- Cache TTL: 5 minutes default
- Serialization format: ISO-8601 for dates (not timestamps)

**Important:** All date/time fields use `java.time.Instant`. Jackson 3 automatically registers `JavaTimeModule` for Instant serialization. Note: Spring Boot 4 uses `tools.jackson` package (Jackson 3), not `com.fasterxml.jackson` (Jackson 2). Import 시 `tools.jackson.databind.ObjectMapper` 사용.

### UTC Timezone Strategy

모든 날짜시간 처리는 UTC로 통일되어 있으며, 5개 레이어에서 설정이 적용됩니다:

1. **JVM**: `common/src/main/kotlin/com/vivire/locapet/common/config/UtcTimeZoneConfig.kt` - `@PostConstruct`에서 `TimeZone.setDefault(UTC)` 호출
2. **JDBC 연결**: `application.yml` > `spring.datasource.hikari.connection-init-sql: SET TIME ZONE 'UTC'`
3. **Hibernate**: `application.yml` > `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`
4. **Jackson**: `application.yml` > `spring.jackson.time-zone: UTC` + `write-dates-as-timestamps: false`
5. **Docker**: `docker-compose.yml`에서 PostgreSQL 컨테이너에 `TZ: UTC`, `PGTZ: UTC`, `command: postgres -c timezone=UTC` 적용

**엔티티/DTO 날짜 타입 규칙:**
- 반드시 `java.time.Instant` 사용 (`LocalDateTime` 사용 금지). 예외: 생년월일(`birth`)은 `java.time.LocalDate` 허용 (타임존 불필요한 날짜 전용)
- DB 컬럼은 `TIMESTAMPTZ` 타입 사용 (V003 마이그레이션으로 기존 `TIMESTAMP` 컬럼 변환 완료)
- JSON 직렬화 포맷: ISO-8601 (`2026-02-25T09:30:00Z`)

### Custom Application Configuration

The `app-api` module uses custom configuration properties defined in `application.yml`:

**`app.auth.jwt`** — JWT 토큰 설정:
- `secret` - HMAC 서명 키 (32자 이상)
- `access-token-expiry` - Access token TTL (기본: 30분)
- `refresh-token-expiry` - Refresh token TTL (기본: 14일)
- `onboarding-token-expiry` - Onboarding JWT TTL (기본: 30분)

**`app.auth.identity-verification`** — PASS 본인인증:
- `provider` - `mock` (로컬/테스트) or `real` (프로덕션 PASS API)
- `ci-hmac-secret` - CI 해시용 HMAC 키 (32자 이상)
- `onboarding-session-ttl` - Redis 온보딩 세션 TTL (기본: 10분)

**`app.auth.social`** — 소셜 로그인 Provider URL 및 client ID (Kakao, Naver, Google, Apple)

**`app.meta`** — 앱 메타데이터 (`MetaConfigProperties`로 로드):
- Store URLs (App Store, Play Store)
- API base URL
- Policy URLs (terms of service, privacy policy)
