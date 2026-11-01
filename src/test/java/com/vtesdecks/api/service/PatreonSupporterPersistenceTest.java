package com.vtesdecks.api.service;

import com.vtesdecks.integration.PatreonMember.Decision;
import com.vtesdecks.jpa.repositories.UserRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatreonSupporterPersistenceTest {
    private LocalContainerEntityManagerFactoryBean factory;
    private JdbcTemplate jdbc;
    private UserRepository users;
    private TransactionTemplate tx;
    private PatreonSupporterService supporters;

    @BeforeAll
    void database() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE `user` (id INT PRIMARY KEY, auth_version BIGINT DEFAULT 0,
                vekn_id VARCHAR(64) UNIQUE, username VARCHAR(255), email VARCHAR(255), password VARCHAR(255),
                display_name VARCHAR(255), profile_image VARCHAR(255), validated BOOLEAN DEFAULT TRUE,
                admin BOOLEAN DEFAULT FALSE, wishlist_public_visibility BOOLEAN DEFAULT TRUE,
                card_printing_preference VARCHAR(32) DEFAULT 'NEWEST', creation_date TIMESTAMP,
                modification_date TIMESTAMP, forgot_password_date TIMESTAMP)
                """);
        for (String statement : Files.readString(Path.of("src/main/resources/db/migration/V1.0.107__user_patreon_id.sql"))
                .replace(" CHARACTER SET ascii COLLATE ascii_bin", "").split(";")) {
            if (!statement.isBlank()) {
                jdbc.execute(statement);
            }
        }
        jdbc.execute("CREATE TABLE role (id INT PRIMARY KEY, name VARCHAR(64))");
        jdbc.execute("CREATE TABLE user_role (user_id INT, role_id INT, PRIMARY KEY (user_id, role_id))");
        jdbc.update("INSERT INTO role VALUES (1, 'supporter'), (2, 'tester')");
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPackagesToScan("com.vtesdecks.jpa.entity");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        factory.afterPropertiesSet();
        users = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory.getObject()))
                .getRepository(UserRepository.class);
        tx = new TransactionTemplate(new JpaTransactionManager(factory.getObject()));
        supporters = new PatreonSupporterService(users, jdbc);
    }

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM user_role");
        jdbc.update("DELETE FROM `user`");
        jdbc.update("INSERT INTO `user` (id, username, email, display_name, password) VALUES (1,'unknown','unknown@example.com','Unknown','hash'), (2,'linked','linked@example.com','Linked','hash')");
        jdbc.update("INSERT INTO user_role VALUES (1,1), (2,1), (2,2)");
        tx.executeWithoutResult(status -> users.updatePatreonId(2, "00123"));
    }

    @AfterAll
    void close() {
        factory.destroy();
    }

    @Test
    void unknownSupporterIsExcludedAndLiveIdCheckProtectsClearedOrChangedLinks() {
        assertEquals(1, users.findPatreonAccounts().size());
        assertEquals(2, users.findPatreonAccounts().getFirst().getId());
        tx.executeWithoutResult(status -> {
            supporters.apply(1, null, Decision.REMOVE);
            supporters.apply(1, "999", Decision.REMOVE);
            users.updatePatreonId(2, null);
            supporters.apply(2, "00123", Decision.REMOVE);
        });
        assertEquals(2, countSupporters());
    }

    @Test
    void repeatedGrantAndRemovalAreIdempotentAndPreserveOtherRolesAndIdentity() {
        tx.executeWithoutResult(status -> {
            supporters.apply(2, "00123", Decision.GRANT);
            supporters.apply(2, "00123", Decision.GRANT);
            supporters.apply(2, "00123", Decision.REMOVE);
            supporters.apply(2, "00123", Decision.REMOVE);
        });
        assertEquals(1, countSupporters());
        assertEquals("00123", users.findPatreonAccounts().getFirst().getPatreonId());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE user_id=2 AND role_id=2", Integer.class));
    }

    @Test
    void unrelatedStaleOrmSaveCannotOverwriteEitherIdentity() {
        var stale = users.findById(2).orElseThrow();
        tx.executeWithoutResult(status -> {
            users.updatePatreonId(2, "00456");
            users.updateVerifiedVeknId(2, "003120065");
        });
        stale.setDisplayName("Updated name");
        tx.executeWithoutResult(status -> users.saveAndFlush(stale));
        assertEquals("00456", jdbc.queryForObject("SELECT patreon_id FROM `user` WHERE id=2", String.class));
        assertEquals("003120065", jdbc.queryForObject("SELECT vekn_id FROM `user` WHERE id=2", String.class));
    }

    @Test
    void migrationAllowsUnlinkedAccountsButEnforcesUniquePatreonIds() {
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE `user` SET patreon_id='00123' WHERE id=1"));
        assertEquals(1, countUnlinked());
    }

    @Test
    void dailyJobPreservesUnlinkedSupporterForActiveCancelledAndAbsentMatchingEmails() {
        var memberships = org.mockito.Mockito.mock(PatreonMembershipService.class);
        org.mockito.Mockito.when(memberships.available()).thenReturn(true);
        var scheduler = new com.vtesdecks.scheduler.PatreonSupporterScheduler(memberships, users, supporters);
        for (Decision state : Decision.values()) {
            var snapshot = new java.util.HashMap<String, com.vtesdecks.integration.PatreonMember>();
            snapshot.put("00123", new com.vtesdecks.integration.PatreonMember("linked", "00123", null, Decision.KEEP));
            if (state != Decision.KEEP) {
                snapshot.put("999", new com.vtesdecks.integration.PatreonMember("unknown", "999", "unknown@example.com", state));
            }
            org.mockito.Mockito.when(memberships.members()).thenReturn(snapshot);
            tx.executeWithoutResult(status -> scheduler.reconcile());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE user_id=1 AND role_id=1", Integer.class));
            assertNull(jdbc.queryForObject("SELECT patreon_id FROM `user` WHERE id=1", String.class));
        }
    }

    private int countSupporters() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM user_role WHERE role_id=1", Integer.class);
    }

    private int countUnlinked() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE patreon_id IS NULL", Integer.class);
    }
}
