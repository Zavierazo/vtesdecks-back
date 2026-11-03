package com.vtesdecks.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CardI18nImageMigrationTest {
    @Test
    void migratesBothCardTypesWithoutLosingTranslationsImagesOrDates() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            verifyMigration(jdbc, () -> new ResourceDatabasePopulator(new ClassPathResource(
                    "db/migration/V1.0.108__separate_card_i18n_images.sql")).execute(dataSource));
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }

    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "vtes.test.mysql.url", matches = "jdbc:mysql://127\\.0\\.0\\.1:.*")
    void migratesOnMysql() throws Exception {
        String schema = "card_i18n_image_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = DriverManager.getConnection(System.getProperty("vtes.test.mysql.url"), "root", "")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }
            connection.setCatalog(schema);
            var dataSource = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
            try {
                verifyMigration(new JdbcTemplate(dataSource), () -> new ResourceDatabasePopulator(new ClassPathResource(
                        "db/migration/V1.0.108__separate_card_i18n_images.sql")).execute(dataSource));
            } finally {
                try (var statement = connection.createStatement()) {
                    statement.execute("DROP DATABASE " + schema);
                }
            }
        }
    }

    private void verifyMigration(JdbcTemplate jdbc, Runnable migrate) {
        for (String table : new String[]{"crypt_i18n", "library_i18n"}) {
            jdbc.execute("CREATE TABLE " + table + " (id INT, locale VARCHAR(2), name VARCHAR(250) NOT NULL, text TEXT, "
                    + "image VARCHAR(250), creation_date TIMESTAMP, modification_date TIMESTAMP, PRIMARY KEY(id, locale))");
        }
        jdbc.update("INSERT INTO crypt_i18n VALUES (200001, 'es', 'Nombre', 'Descripción', '/img/cards/es/200001.jpg', '2026-10-01 01:02:03', '2026-10-02 04:05:06')");
        jdbc.update("INSERT INTO crypt_i18n VALUES (200002, 'fr', 'Sans image', 'Texte', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO library_i18n VALUES (100001, 'fr', 'Nom', 'Texte', '/img/cards/fr/100001.jpg', '2026-09-01 01:02:03', '2026-09-02 04:05:06')");
        jdbc.update("INSERT INTO library_i18n VALUES (100002, 'pt', 'Sem imagem', 'Texto', '  ', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        migrate.run();

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM card_i18n_image", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM crypt_i18n", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM library_i18n", Integer.class));
        assertEquals("Descripción", jdbc.queryForObject("SELECT text FROM crypt_i18n WHERE id = 200001", String.class));
        assertEquals("Nom", jdbc.queryForObject("SELECT name FROM library_i18n WHERE id = 100001", String.class));
        assertEquals("/img/cards/es/200001.jpg", jdbc.queryForObject("SELECT image FROM card_i18n_image WHERE card_id = 200001", String.class));
        assertEquals(java.sql.Timestamp.valueOf("2026-10-01 01:02:03"), jdbc.queryForObject(
                "SELECT creation_date FROM card_i18n_image WHERE card_id = 200001", java.sql.Timestamp.class));
        assertEquals(java.sql.Timestamp.valueOf("2026-09-02 04:05:06"), jdbc.queryForObject(
                "SELECT modification_date FROM card_i18n_image WHERE card_id = 100001", java.sql.Timestamp.class));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.queryForList("SELECT image FROM crypt_i18n"));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.queryForList("SELECT image FROM library_i18n"));

        jdbc.update("DELETE FROM crypt_i18n WHERE id = 200001");

        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM card_i18n_image", Integer.class));
    }
}
