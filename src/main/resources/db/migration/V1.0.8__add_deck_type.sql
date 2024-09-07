ALTER TABLE `deck` ADD COLUMN type varchar(250) AFTER `id`;
UPDATE deck SET type = 'TOURNAMENT';
