ALTER TABLE `user`
    ADD COLUMN `vekn_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL;

CREATE UNIQUE INDEX `uk_user_vekn_id` ON `user` (`vekn_id`);
