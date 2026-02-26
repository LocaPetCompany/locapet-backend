-- ============================================================
-- 기존 members 테이블 완전 재구축 (초기 단계, 데이터 이관 불필요)
-- ============================================================

DROP TABLE IF EXISTS members CASCADE;

CREATE TABLE members (
    id                      BIGSERIAL PRIMARY KEY,
    account_status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    onboarding_stage        VARCHAR(30) NOT NULL DEFAULT 'PROFILE_REQUIRED',
    ci_hash                 VARCHAR(128),
    phone                   VARCHAR(20),
    name                    VARCHAR(100),
    birth                   DATE,
    nickname                VARCHAR(50),
    email                   VARCHAR(255),
    profile_image_url       VARCHAR(500),
    role                    VARCHAR(20) NOT NULL DEFAULT 'USER',
    terms_of_service_agreed BOOLEAN NOT NULL DEFAULT FALSE,
    privacy_policy_agreed   BOOLEAN NOT NULL DEFAULT FALSE,
    marketing_consent       BOOLEAN NOT NULL DEFAULT FALSE,
    terms_agreed_at         TIMESTAMPTZ,
    withdrawal_type         VARCHAR(20),
    withdrawal_requested_at TIMESTAMPTZ,
    withdrawal_effective_at TIMESTAMPTZ,
    withdrawn_at            TIMESTAMPTZ,
    force_withdrawn_at      TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_members_ci_hash ON members (ci_hash) WHERE ci_hash IS NOT NULL;
CREATE INDEX idx_members_account_status ON members (account_status);
CREATE INDEX idx_members_nickname ON members (nickname);

-- ============================================================
-- social_accounts
-- ============================================================

CREATE TABLE social_accounts (
    id               BIGSERIAL PRIMARY KEY,
    provider         VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    member_id        BIGINT NOT NULL REFERENCES members(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_social_provider_user UNIQUE (provider, provider_user_id),
    CONSTRAINT uk_social_provider_member UNIQUE (provider, member_id)
);

-- ============================================================
-- identity_locks (CI 잠금 정책 관리)
-- ============================================================

CREATE TABLE identity_locks (
    ci_hash      VARCHAR(128) PRIMARY KEY,
    lock_type    VARCHAR(20) NOT NULL,
    locked_until TIMESTAMPTZ,
    reason       VARCHAR(50) NOT NULL,
    member_id    BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- identity_verifications (감사 로그)
-- ============================================================

CREATE TABLE identity_verifications (
    id             BIGSERIAL PRIMARY KEY,
    member_id      BIGINT,
    vendor         VARCHAR(50) NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    ci_hash        VARCHAR(128),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_iv_member ON identity_verifications (member_id);
CREATE INDEX idx_iv_transaction ON identity_verifications (transaction_id);
