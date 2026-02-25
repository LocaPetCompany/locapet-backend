# locapet-backend
로카펫 백엔드

## UTC 운영 기준

애플리케이션, DB, 컨테이너/ECS를 모두 UTC로 고정한다.

### 1) Spring 애플리케이션 설정

- `app-api` / `admin-api`
  - `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`
  - `spring.jackson.time-zone=UTC`
  - `spring.jackson.serialization.write-dates-as-timestamps=false`
- `common` 모듈
  - `UtcTimeZoneConfig`에서 JVM 기본 타임존을 `UTC`로 설정

### 2) DB(PostgreSQL) 설정

- Flyway `V003__convert_timestamp_columns_to_timestamptz.sql`로 기존 `TIMESTAMP` 컬럼을 `TIMESTAMPTZ`로 변환
- 변환 시 기존 값은 UTC 기준이었다고 가정하고 `AT TIME ZONE 'UTC'`로 보정
- ECS/RDS 운영 시 PostgreSQL 파라미터 `timezone=UTC` 유지

### 3) ECS Task Definition 권장 설정

컨테이너 환경 변수에 아래 값을 넣는다.

- `TZ=UTC`
- `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC`

IaC 저장소에서 위 환경 변수를 명시하고, 서비스 배포 시 UTC 정책이 유지되도록 고정한다.

### 4) Docker 관련 원칙

- Dockerfile이 있으면 `ENV TZ=UTC`를 명시하는 것을 권장
- Dockerfile이 없어도 ECS Task env(`TZ`, `JAVA_TOOL_OPTIONS`)로 동일 정책 적용 가능
- 로컬 `docker-compose.yml`의 PostgreSQL도 `timezone=UTC`로 고정
