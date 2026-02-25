-- Create app_versions table
CREATE TABLE app_versions (
    id BIGSERIAL PRIMARY KEY,
    version VARCHAR(20) NOT NULL UNIQUE,
    force_update BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_version VARCHAR(20),
    platform VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_platform_active ON app_versions (platform, is_active);

-- Create maintenances table
CREATE TABLE maintenances (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_time_active ON maintenances (start_time, end_time, is_active);

-- Create notices table
CREATE TABLE notices (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    router_url VARCHAR(500),
    notice_type VARCHAR(20) NOT NULL,
    priority INT NOT NULL DEFAULT 5,
    display_start_time TIMESTAMP NOT NULL,
    display_end_time TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_display_priority ON notices (display_start_time, display_end_time, priority DESC);

-- Insert initial version data
INSERT INTO app_versions (version, force_update, platform, is_active, created_at, updated_at)
VALUES ('1.0.0', FALSE, 'ALL', TRUE, NOW(), NOW());
