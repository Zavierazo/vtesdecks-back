CREATE TABLE user_achievement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id INT NOT NULL,
    achievement_id VARCHAR(80) NOT NULL,
    family VARCHAR(50) NOT NULL,
    tier_value INT NOT NULL,
    occurrence_key VARCHAR(30) NOT NULL DEFAULT '',
    historical BOOLEAN NOT NULL DEFAULT FALSE,
    earned_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_user_achievement UNIQUE (user_id, achievement_id, occurrence_key),
    CONSTRAINT fk_user_achievement_user FOREIGN KEY (user_id) REFERENCES user(id)
);

CREATE INDEX idx_user_achievement_user_family ON user_achievement(user_id, family);
