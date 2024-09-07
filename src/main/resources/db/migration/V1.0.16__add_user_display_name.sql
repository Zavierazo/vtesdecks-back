ALTER TABLE `user` ADD COLUMN display_name varchar(250) AFTER `login_hash`;
UPDATE user SET display_name = username;
ALTER TABLE `user` MODIFY display_name varchar(250) NOT NULL;
