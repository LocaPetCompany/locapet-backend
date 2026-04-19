-- PostGIS (Place 도메인 공간 검색) + pg_trgm (Place 키워드 검색) 확장 설치
-- AWS RDS 에서는 rds_superuser 권한 필요. 로컬/Testcontainers 는 postgis/postgis 이미지라 자동 가용.

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
