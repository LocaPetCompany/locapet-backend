CREATE TABLE members (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    social_id VARCHAR(255) NOT NULL,
    social_provider VARCHAR(20) NOT NULL,
    email VARCHAR(255),
    nickname VARCHAR(50),
    profile_image_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    withdrawn_at DATETIME,
    UNIQUE INDEX uk_social_id_provider (social_id, social_provider),
    INDEX idx_status (status),
    INDEX idx_nickname (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
