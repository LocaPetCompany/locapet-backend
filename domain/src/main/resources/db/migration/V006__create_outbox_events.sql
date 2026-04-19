-- ============================================================
-- outbox_events
--   - 도메인 이벤트 (REVIEW_LIKED, NOTICE_PUBLISHED, INQUIRY_ANSWERED 등) 발행 테이블
--   - 트랜잭션 내 INSERT → AFTER_COMMIT 리스너 or 폴러가 PENDING 건 소비
--   - dedupe_key 로 24h 내 동일 이벤트 중복 차단 (09-notification-spec.md §7)
--   - 성공 후 published_at 설정. 90일 지난 PUBLISHED 는 배치 삭제 (M7 PR-28)
-- ============================================================

CREATE TABLE outbox_events (
    id             BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    payload        JSONB        NOT NULL,
    dedupe_key     VARCHAR(200),
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempt_count  INT          NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- PENDING 이벤트 폴링 (오래된 순)
CREATE INDEX idx_outbox_pending_created
    ON outbox_events (created_at ASC)
    WHERE status = 'PENDING';

-- dedupe_key UNIQUE 는 NULL 허용 부분 인덱스
CREATE UNIQUE INDEX uk_outbox_dedupe_key
    ON outbox_events (dedupe_key)
    WHERE dedupe_key IS NOT NULL;
