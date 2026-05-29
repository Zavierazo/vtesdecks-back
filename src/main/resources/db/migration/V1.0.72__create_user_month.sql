CREATE TABLE user_month (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    month_date DATE NOT NULL,
    `rank` INT NOT NULL,
    score BIGINT NOT NULL DEFAULT 0,
    creation_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_month_user FOREIGN KEY (user_id) REFERENCES user(id)
);

CREATE INDEX idx_user_month_month_date ON user_month(month_date);


