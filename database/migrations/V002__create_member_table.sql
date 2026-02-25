CREATE TABLE members (
    id BIGSERIAL PRIMARY KEY,
    social_id VARCHAR(255) NOT NULL,
    social_provider VARCHAR(20) NOT NULL,
    email VARCHAR(255),
    nickname VARCHAR(50),
    profile_image_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    withdrawn_at TIMESTAMP
);

CREATE UNIQUE INDEX uk_social_id_provider ON members (social_id, social_provider);
CREATE INDEX idx_status ON members (status);
CREATE INDEX idx_nickname ON members (nickname);
