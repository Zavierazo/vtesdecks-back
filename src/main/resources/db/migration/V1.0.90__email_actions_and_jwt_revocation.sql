ALTER TABLE `user` ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE user_email_action (
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    user_id INT NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    target_email VARCHAR(320) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_email_action_user FOREIGN KEY (user_id) REFERENCES `user` (id) ON DELETE CASCADE,
    INDEX idx_email_action_user_purpose (user_id, purpose, created_at),
    INDEX idx_email_action_expiry (expires_at)
);
