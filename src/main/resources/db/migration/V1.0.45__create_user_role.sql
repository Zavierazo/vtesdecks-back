CREATE TABLE user_role (
  id int NOT NULL AUTO_INCREMENT,
  user_id int NOT NULL,
  role_id int NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT user_role_user_id_fk FOREIGN KEY (user_id) REFERENCES `user` (id),
  CONSTRAINT user_role_role_id_fk FOREIGN KEY (role_id) REFERENCES role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX user_roles_user_id_idx ON user_role(user_id);