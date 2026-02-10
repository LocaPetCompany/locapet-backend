# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Locapet Backend - A multi-module Kotlin/Spring Boot backend service for the Locapet application. The project uses Gradle with Kotlin DSL for build management.

**Technology Stack:**
- Kotlin 2.2.21
- Spring Boot 4.0.1
- JDK 21
- MySQL 8.0 database
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
- Share the same MySQL database (`locapet`)
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
- Tests use **Testcontainers** for MySQL and Redis
- Base class: `IntegrationTestSupport` (note: typo in filename `IntergrationTestSupport.kt`)
- Docker must be running for tests to execute
- Tests automatically start MySQL 8.0 and Redis containers

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
./gradlew :app-api:test --tests "InfrastructureTest.MySQL 컨테이너가 정상적으로 연결되어야 한다"

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
- **MySQL** running on `localhost:3306`
  - Database: `locapet`
  - Username: `root`
  - Password: `password`
  - Timezone: `Asia/Seoul`
  - Character encoding: `UTF-8`
- **Redis** running on `localhost:6379`
- **Docker** (required for integration tests with Testcontainers)

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

## Important Implementation Details

### Jackson & Redis Serialization

This project uses **Spring Boot 4.0.1** which includes both Jackson 2 and Jackson 3:
- HTTP JSON serialization: Automatic via Spring Boot
- Redis cache serialization: Custom configuration required
- `CacheConfig.kt` configures `GenericJackson2JsonRedisSerializer` with `JavaTimeModule` for `LocalDateTime` support
- Cache TTL: 5 minutes default
- Serialization format: ISO-8601 for dates (not timestamps)

**Important:** When caching objects with `LocalDateTime`, `LocalDate`, or other Java 8 time types, ensure the ObjectMapper includes `JavaTimeModule`.

### Custom Application Configuration

The `app-api` module uses custom configuration properties under the `app.meta` prefix:
- Store URLs (App Store, Play Store)
- API base URL
- Policy URLs (terms of service, privacy policy)

These are defined in `application.yml` and loaded via `MetaConfigProperties`.
