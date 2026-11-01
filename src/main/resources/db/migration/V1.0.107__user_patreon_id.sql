ALTER TABLE `user`
    ADD COLUMN `patreon_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;

CREATE UNIQUE INDEX `uk_user_patreon_id` ON `user` (`patreon_id`);
