ALTER TABLE `deck` ADD COLUMN `user` int AFTER `type`;
ALTER TABLE `deck` ADD CONSTRAINT fk_deck_user FOREIGN KEY (user) REFERENCES `user`(id);
