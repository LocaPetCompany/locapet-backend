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

## Environment Profiles
- Spring profiles are split into `local`, `dev`, `stg`, `prd` for both `app-api` and `admin-api`.
- Default profile is `local` (`spring.profiles.default=local`).
- Config file layout:
  - `app-api/src/main/resources/application.yml`
  - `app-api/src/main/resources/application-{local,dev,stg,prd}.yml`
  - `admin-api/src/main/resources/application.yml`
  - `admin-api/src/main/resources/application-{local,dev,stg,prd}.yml`
- `application.yml` keeps shared config only; environment-specific values belong in `application-{profile}.yml`.
- `dev/stg/prd` files must not contain real secrets; keep placeholders and inject real values via environment variables/secret manager at deploy time.
- Example runs:
  - `gradle :app-api:bootRun` (default `local`)
  - `gradle :app-api:bootRun --args='--spring.profiles.active=dev'`
  - `gradle :admin-api:bootRun --args='--spring.profiles.active=stg'`

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

## Current Auth/Onboarding Plan (Decision-Complete)

### App Bootstrap Flow
- On app launch, call `meta` and `session` in parallel.
- `GET /api/v1/meta/splash`: maintenance/version/policy gate.
- `GET /api/v1/auth/session`: auto-login/session routing.
- If `session` is `401`, call `POST /api/v1/auth/reissue` then retry `session`.
- If reissue fails, clear local tokens and route to login.

### Login/Signup UX Policy
- No separate signup button/page.
- Social login buttons (`Kakao/Google/Apple/Naver`) are the single entry for both login and signup.
- Routing is based on server `result`:
  - `NEEDS_IDENTITY_VERIFICATION`
  - `NEEDS_PROFILE`
  - `COMPLETED`

### Member State Model (Split Axes)
- `account_status`: `ACTIVE`, `DELETION_SCHEDULED`, `DELETED`, `BANNED`
- `onboarding_stage`: `IDENTITY_REQUIRED`, `PROFILE_REQUIRED`, `COMPLETED`

### Social Linking Policy
- CI-based multi-social linking is allowed.
- If CI matches an existing member (and not locked), auto-link the new social account to that member.
- DB uniqueness:
  - `social_accounts(provider, provider_user_id)` unique
  - `social_accounts.user_id` is NOT unique (multi-provider allowed)

### Withdrawal/Rejoin Policy
- Forced withdrawal (`BANNED`): rejoin permanently blocked.
- Voluntary withdrawal:
  - request -> 30-day grace (`DELETION_SCHEDULED`)
  - after grace -> `DELETED`
  - rejoin allowed only after an additional 30 days from `DELETED`

### Identity Verification Integration
- Use provider abstraction:
  - `IdentityVerificationProvider.verify(transactionId) -> IdentityResult`
- Implementations:
  - `MockPassProvider` (current development/QA)
  - `RealPassProvider` (post-contract switch)
- Store CI as hash only: `ci_hash = HMAC(secret, CI)` (no raw CI storage).

### API Baseline
- `POST /api/v1/auth/social/{provider}`
- `POST /api/v1/onboarding/identity/verify`
- `POST /api/v1/onboarding/profile/complete`
- `GET /api/v1/auth/session`
- `POST /api/v1/auth/reissue`
- `POST /api/v1/auth/logout`
