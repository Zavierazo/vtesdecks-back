CREATE TABLE card_i18n_image (
  card_id int NOT NULL,
  locale varchar(2) NOT NULL,
  image varchar(250) NOT NULL,
  creation_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modification_date timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (card_id, locale)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO card_i18n_image (card_id, locale, image, creation_date, modification_date)
SELECT id, locale, image, creation_date, modification_date
FROM crypt_i18n
WHERE image IS NOT NULL AND TRIM(image) <> '';

INSERT INTO card_i18n_image (card_id, locale, image, creation_date, modification_date)
SELECT id, locale, image, creation_date, modification_date
FROM library_i18n
WHERE image IS NOT NULL AND TRIM(image) <> '';

ALTER TABLE crypt_i18n DROP COLUMN image;
ALTER TABLE library_i18n DROP COLUMN image;
