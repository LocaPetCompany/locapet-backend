# ==============================================================================
# Multi-stage Dockerfile for Locapet Backend
# Usage:
#   docker build --build-arg MODULE=app-api -t locapet-app-api .
#   docker build --build-arg MODULE=admin-api -t locapet-admin-api .
# ==============================================================================

# --- Stage 1: Build ---
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

ARG MODULE

# Gradle wrapper + build scripts (캐시 레이어)
COPY gradlew gradlew.bat ./
COPY gradle/wrapper/ gradle/wrapper/
COPY build.gradle.kts settings.gradle.kts ./
COPY app-api/build.gradle.kts app-api/
COPY admin-api/build.gradle.kts admin-api/
COPY domain/build.gradle.kts domain/
COPY common/build.gradle.kts common/

# 의존성 다운로드 (소스 변경 시 캐시 재사용)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# 전체 소스 복사 + 빌드
COPY . .
RUN ./gradlew :${MODULE}:bootJar -x test --no-daemon

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine

# 타임존 설정
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/UTC /etc/localtime && \
    echo "UTC" > /etc/timezone && \
    apk del tzdata

# non-root 사용자
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

ARG MODULE
COPY --from=builder /app/${MODULE}/build/libs/*.jar app.jar

ENV JAVA_OPTS="-Duser.timezone=UTC -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
