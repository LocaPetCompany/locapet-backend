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
  - `api/` - REST controllers organized by domain (auth, member, meta)
  - `application/` - Application services
  - `global/config/` - Spring configuration classes
- `domain/`: JPA entities and repositories
  - Organized by domain (e.g., `meta/` for app metadata)
- `common/`: Shared utilities and configurations

## Database Management

**Schema Management:**
- JPA uses `ddl-auto: validate` - Hibernate validates schema but doesn't modify it
- SQL migrations are located in `database/migrations/`
- Migration files follow Flyway naming: `V001__create_meta_tables.sql`
- Schema changes must be done via migration files, not JPA auto-DDL
- SQL logging is enabled for development (`show-sql: true`, `format_sql: true`)

**Current Tables:**
- `app_versions` - App version management with force update support
- `maintenances` - Scheduled maintenance notifications
- `notices` - In-app notices with display scheduling

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
- Redis cache serialization: Custom configuration required
- `CacheConfig.kt` configures `GenericJacksonJsonRedisSerializer` (Jackson 3) using `ObjectMapper().findAndRegisterModules()` for automatic module registration
- Cache TTL: 5 minutes default
- Serialization format: ISO-8601 for dates (not timestamps)

**Important:** All date/time fields use `java.time.Instant`. `findAndRegisterModules()` ensures Instant is correctly serialized/deserialized in Redis cache. Note: Spring Boot 4 uses `tools.jackson` package (Jackson 3), not `com.fasterxml.jackson` (Jackson 2).

### UTC Timezone Strategy

모든 날짜시간 처리는 UTC로 통일되어 있으며, 5개 레이어에서 설정이 적용됩니다:

1. **JVM**: `common/src/main/kotlin/com/vivire/locapet/common/config/UtcTimeZoneConfig.kt` - `@PostConstruct`에서 `TimeZone.setDefault(UTC)` 호출
2. **JDBC 연결**: `application.yml` > `spring.datasource.hikari.connection-init-sql: SET TIME ZONE 'UTC'`
3. **Hibernate**: `application.yml` > `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`
4. **Jackson**: `application.yml` > `spring.jackson.time-zone: UTC` + `write-dates-as-timestamps: false`
5. **Docker**: `docker-compose.yml`에서 PostgreSQL 컨테이너에 `TZ: UTC`, `PGTZ: UTC`, `command: postgres -c timezone=UTC` 적용

**엔티티/DTO 날짜 타입 규칙:**
- 반드시 `java.time.Instant` 사용 (`LocalDateTime`, `LocalDate` 사용 금지)
- DB 컬럼은 `TIMESTAMPTZ` 타입 사용 (V003 마이그레이션으로 기존 `TIMESTAMP` 컬럼 변환 완료)
- JSON 직렬화 포맷: ISO-8601 (`2026-02-25T09:30:00Z`)

### Custom Application Configuration

The `app-api` module uses custom configuration properties under the `app.meta` prefix:
- Store URLs (App Store, Play Store)
- API base URL
- Policy URLs (terms of service, privacy policy)

These are defined in `application.yml` and loaded via `MetaConfigProperties`.
