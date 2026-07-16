CREATE TABLE IF NOT EXISTS `feature_flag` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `flag_key` VARCHAR(64) NOT NULL,
  `type` ENUM('BOOLEAN','STRING','LIST') NOT NULL,
  `value` JSON NOT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `creation_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `modification_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_feature_flag_key`(`flag_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `feature_flag` (`flag_key`, `type`, `value`, `description`)
VALUES
    ('home_ad', 'BOOLEAN', 'false', 'Enable custom ad on home page (overrides AdSense below hero section)'),
    ('home_ad_url', 'STRING', '"https://www.youtube.com/@VTES_ES"', 'Link opened when clicking the home custom ad'),
    ('home_ad_image', 'STRING', '"https://cdn.vtesdecks.com/img/sponsors/conclave/main.png"', 'Desktop image URL for the home custom ad'),
    ('home_ad_image_mobile', 'STRING', '"https://cdn.vtesdecks.com/img/sponsors/conclave/mobile.png"', 'Mobile image URL for the home custom ad'),
    ('home_ad_countries', 'LIST', '[]', 'ISO country codes that can see the home custom ad (empty = everyone)');
