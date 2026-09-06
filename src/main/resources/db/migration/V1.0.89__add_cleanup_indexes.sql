CREATE INDEX idx_user_notification_user_creation_id
    ON user_notification (`user`, creation_date, id);

CREATE INDEX idx_user_notification_read_creation_id
    ON user_notification (`read`, creation_date, id);

CREATE INDEX idx_comment_deleted_modification_id
    ON comment (deleted, modification_date, id);
