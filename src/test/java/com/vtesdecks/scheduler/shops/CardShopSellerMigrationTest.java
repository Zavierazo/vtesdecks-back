package com.vtesdecks.scheduler.shops;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "vtes.test.mysql.url", matches = "jdbc:mysql://127\\.0\\.0\\.1:.*")
class CardShopSellerMigrationTest {
    private final String schema = "card_shop_seller_test_" + UUID.randomUUID().toString().replace("-", "");
    private Connection connection;

    @BeforeEach
    void createIsolatedSchema() throws Exception {
        connection = DriverManager.getConnection(System.getProperty("vtes.test.mysql.url"), "root", "");
        execute("CREATE DATABASE " + schema + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        connection.setCatalog(schema);
        execute("""
                CREATE TABLE card_shop (
                    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                    card_id INT NOT NULL,
                    platform VARCHAR(50) NOT NULL,
                    `set` VARCHAR(64) NULL,
                    locale VARCHAR(10) NULL,
                    price DECIMAL(10, 2) NULL,
                    UNIQUE KEY card_shop_card_unique (card_id, platform, `set`, locale)
                ) ENGINE=InnoDB
                """);
    }

    @AfterEach
    void removeIsolatedSchema() throws Exception {
        if (connection != null) {
            try (Connection closing = connection) {
                execute("DROP DATABASE IF EXISTS " + schema);
            }
        }
    }

    @Test
    void preservesRowsAndAddsThreeNullableColumnsWithoutChangingThePrimaryKey() throws Exception {
        execute("INSERT INTO card_shop (card_id, platform, `set`, locale, price) VALUES (100545, 'EBAY', NULL, NULL, 1.50)");

        migrate();

        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT * FROM card_shop")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt("id"));
            assertEquals(100545, rows.getInt("card_id"));
            assertNull(rows.getString("seller"));
            assertNull(rows.getString("country"));
            assertNull(rows.getString("continent"));
            assertFalse(rows.next());
        }
        for (String column : new String[]{"seller", "country", "continent"}) {
            try (var columns = connection.getMetaData().getColumns(schema, null, "card_shop", column)) {
                assertTrue(columns.next());
                assertEquals(DatabaseMetaData.columnNullable, columns.getInt("NULLABLE"));
                assertEquals(column.equals("seller") ? 255 : 2, columns.getInt("COLUMN_SIZE"));
            }
        }
        try (var keys = connection.getMetaData().getPrimaryKeys(schema, null, "card_shop")) {
            assertTrue(keys.next());
            assertEquals("id", keys.getString("COLUMN_NAME"));
            assertFalse(keys.next());
        }
    }

    @Test
    void allowsDifferentSellersButRejectsTheSameSellerWithNullablePrintingAndLanguage() throws Exception {
        migrate();
        insert(null, null, "first", "ES", "EU");
        insert(null, null, "second", "ES", "EU");

        SQLException duplicate = assertThrows(SQLException.class, () -> insert(null, null, "first", "US", "NA"));

        assertEquals("23000", duplicate.getSQLState());
        assertEquals(2, countRows());
    }

    @Test
    void enforcesUnknownSellerUniquenessAndAllowsLocationUpdates() throws Exception {
        migrate();
        insert("Jyhad", "en", null, "ES", "EU");

        assertThrows(SQLException.class, () -> insert("Jyhad", "en", null, "US", "NA"));
        execute("UPDATE card_shop SET country = 'US', continent = 'NA' WHERE id = 1");

        assertEquals(1, countRows());
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT country, continent, seller FROM card_shop")) {
            assertTrue(rows.next());
            assertEquals("US", rows.getString("country"));
            assertEquals("NA", rows.getString("continent"));
            assertNull(rows.getString("seller"));
        }
        insert(null, null, null, null, null);
        assertThrows(SQLException.class, () -> insert(null, null, null, "FR", "EU"));
    }

    @Test
    void rejectsLegacyDuplicatesAtomicallyWithoutDeletingRowsOrReplacingTheIndex() throws Exception {
        execute("INSERT INTO card_shop (card_id, platform) VALUES (100545, 'EBAY'), (100545, 'EBAY')");

        assertThrows(SQLException.class, this::migrate);

        assertEquals(2, countRows());
        try (var columns = connection.getMetaData().getColumns(schema, null, "card_shop", "seller")) {
            assertFalse(columns.next());
        }
        try (var statement = connection.createStatement(); var indexes = statement.executeQuery("SHOW INDEX FROM card_shop WHERE Key_name = 'card_shop_card_unique'")) {
            int columns = 0;
            while (indexes.next()) {
                assertNotNull(indexes.getString("Column_name"));
                columns++;
            }
            assertEquals(4, columns);
        }
    }

    private void migrate() throws Exception {
        String sql = new ClassPathResource("db/migration/V1.0.106__add_card_shop_seller_and_location.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        execute(sql);
    }

    private void insert(String set, String locale, String seller, String country, String continent) throws SQLException {
        try (var insert = connection.prepareStatement("INSERT INTO card_shop (card_id, platform, `set`, locale, seller, country, continent) VALUES (100545, 'EBAY', ?, ?, ?, ?, ?)")) {
            insert.setString(1, set);
            insert.setString(2, locale);
            insert.setString(3, seller);
            insert.setString(4, country);
            insert.setString(5, continent);
            insert.executeUpdate();
        }
    }

    private int countRows() throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM card_shop")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private void execute(String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
