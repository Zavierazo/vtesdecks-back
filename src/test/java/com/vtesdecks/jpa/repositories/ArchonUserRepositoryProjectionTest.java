package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.ArchonUserEntity;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArchonUserRepositoryProjectionTest {
    @Test
    void readsOnlyNamesAndIdsUsingTheDerivedProjectionQuery() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // Deliberately omit other player columns: the projection must not load complete entities.
        jdbc.execute("CREATE TABLE archon_user (vekn_id VARCHAR(64) PRIMARY KEY, name VARCHAR(255))");
        jdbc.update("INSERT INTO archon_user VALUES ('003120065', 'Pedro Coelho')");
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setManagedTypes(PersistenceManagedTypes.of(ArchonUserEntity.class.getName()));
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        try {
            factory.afterPropertiesSet();
            try (var entityManager = factory.getObject().createEntityManager()) {
                ArchonUserRepository repository = new JpaRepositoryFactory(entityManager).getRepository(ArchonUserRepository.class);
                var players = repository.findAllProjectedBy();
                assertEquals(1, players.size());
                assertEquals("003120065", players.getFirst().getVeknId());
                assertEquals("Pedro Coelho", players.getFirst().getName());
            }
        } finally {
            factory.destroy();
            jdbc.execute("SHUTDOWN");
        }
    }
}
