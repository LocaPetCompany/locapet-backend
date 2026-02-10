-- Create app_versions table
CREATE TABLE app_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version VARCHAR(20) NOT NULL UNIQUE,
    force_update BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_version VARCHAR(20),
    platform VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_platform_active (platform, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Create maintenances table
CREATE TABLE maintenances (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_time_active (start_time, end_time, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Create notices table
CREATE TABLE notices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    router_url VARCHAR(500),
    notice_type VARCHAR(20) NOT NULL,
    priority INT NOT NULL DEFAULT 5,
    display_start_time DATETIME NOT NULL,
    display_end_time DATETIME NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    INDEX idx_display_priority (display_start_time, display_end_time, priority DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Insert initial version data
INSERT INTO app_versions (version, force_update, platform, is_active, created_at, updated_at)
VALUES ('1.0.0', FALSE, 'ALL', TRUE, NOW(), NOW());
