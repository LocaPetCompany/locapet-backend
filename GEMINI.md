# Locapet Backend Project Context

## Project Overview
**Locapet Backend** is a multi-module Spring Boot application written in Kotlin. It serves as the backend for the Locapet platform, split into distinct modules for application API, admin API, domain logic, and common utilities.

## Tech Stack
*   **Language:** Kotlin (JVM Target 21)
*   **Framework:** Spring Boot 4.0.1
*   **Build Tool:** Gradle (Kotlin DSL)
*   **Database:** MySQL 8.0
*   **Cache:** Redis 7-alpine
*   **Testing:** JUnit 5, Testcontainers

## Architecture (Modules)
The project is structured into the following Gradle modules:

*   **`app-api`**: The main user-facing API application.
    *   Dependencies: `domain`, `common`, Web, Data JPA, Redis.
*   **`admin-api`**: The administration API application.
    *   Dependencies: `domain`, `common`, WebMVC, Data JPA, Redis.
*   **`domain`**: Contains the core domain entities, repositories, and logic. Shared by API modules.
    *   This is a library module (`bootJar` is disabled).
*   **`common`**: Shared utilities and helper classes.

## Infrastructure
The project uses `docker-compose.yml` to define external dependencies:
*   **MySQL**: Port `3306`
*   **Redis**: Port `6379`

## Getting Started

### Prerequisites
*   JDK 21
*   Docker & Docker Compose

### Setup Infrastructure
Start the required databases (MySQL, Redis):
```bash
docker-compose up -d
```

### Running the Applications
To run the User App API:
```bash
./gradlew :app-api:bootRun
```

To run the Admin API:
```bash
./gradlew :admin-api:bootRun
```

### Building
To build the entire project:
```bash
./gradlew build
```

## Testing
The project uses `JUnit 5` and `Testcontainers` for integration testing (specifically for MySQL).

Run all tests:
```bash
./gradlew test
```

## Development Conventions
*   **Package Structure:** `com.vivire.locapet`
*   **Component Scanning:** Applications scan `com.vivire.locapet` to include components from `domain` and `common`.
*   **Gradle DSL:** All build scripts use Kotlin DSL (`.kts`).
