# Repository Guidelines

## Project Structure & Module Organization
- `app-api/`: client-facing Spring Boot API (auth, member, meta endpoints).
- `admin-api/`: admin Spring Boot API.
- `domain/`: JPA entities and repositories shared by APIs.
- `common/`: shared config and reusable components.
- `database/migrations/`: Flyway SQL migrations (`V###__description.sql`).
- `docker-compose.yml`: local PostgreSQL/Redis dependencies.

Source code lives in `*/src/main/kotlin`, and tests live in `*/src/test/kotlin`.

## Build, Test, and Development Commands
This repository currently uses the `gradle` CLI (no committed `gradlew` script).

- `gradle clean build`: compile all modules and build artifacts.
- `gradle test`: run all tests.
- `gradle :app-api:bootRun`: run app API locally (default port `8080`).
- `gradle :admin-api:bootRun`: run admin API locally (default port `8081`).
- `docker compose up -d postgres redis`: start local infra services.

## Coding Style & Naming Conventions
- Language: Kotlin (JDK 21), Spring Boot multi-module Gradle project.
- Indentation: 4 spaces; keep lines and functions concise.
- Naming:
  - Classes/Enums: `PascalCase`
  - Functions/variables/properties: `camelCase`
  - Packages: lowercase (`com.vivire.locapet...`)
- Keep time handling in UTC (`Instant`, DB `TIMESTAMPTZ`) unless a feature explicitly requires local time semantics.

## Testing Guidelines
- Frameworks: JUnit 5 + Spring Boot Test; Testcontainers is used for integration-style tests.
- Place tests under each module’s `src/test/kotlin`.
- Prefer descriptive names like `XxxServiceTest`, `XxxIntegrationTest`.
- Run module-scoped tests with `gradle :app-api:test` (or another module path).

## Commit & Pull Request Guidelines
- Follow existing commit prefixes: `feat:`, `fix:`, `refactor:`, `chore:`.
- Keep commits focused and atomic; include migration files in the same commit when schema changes are introduced.
- PRs should include:
  - summary of behavior changes,
  - affected modules,
  - DB migration impact (if any),
  - API contract changes (request/response, timestamp format),
  - linked issue/ticket.

## Security & Configuration Tips
- Do not commit real credentials/secrets. Use environment variables or secret managers in ECS.
- For deployment, keep UTC settings explicit at runtime (`TZ=UTC`, `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC`).
