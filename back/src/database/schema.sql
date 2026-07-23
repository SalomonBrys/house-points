CREATE TABLE hp_users (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('teacher', 'admin') NOT NULL,
    display_name VARCHAR(190) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE hp_houses (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE hp_point_events (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    house_id INT UNSIGNED NOT NULL,
    teacher_id INT UNSIGNED NOT NULL,
    points INT NOT NULL,
    comment VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_point_events_points_nonzero CHECK (points <> 0),
    FOREIGN KEY (house_id) REFERENCES hp_houses(id),
    FOREIGN KEY (teacher_id) REFERENCES hp_users(id),
    INDEX idx_point_events_house_created (house_id, created_at),
    INDEX idx_point_events_teacher_created (teacher_id, created_at)
);

CREATE TABLE hp_refresh_tokens (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id INT UNSIGNED NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES hp_users(id),
    UNIQUE INDEX idx_refresh_tokens_token_hash (token_hash)
);
