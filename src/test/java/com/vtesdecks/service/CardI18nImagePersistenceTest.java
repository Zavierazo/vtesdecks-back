package com.vtesdecks.service;

import com.vtesdecks.jpa.entity.CardI18nImageEntity.CardI18nImageId;
import com.vtesdecks.jpa.repositories.CardI18nImageRepository;
import com.vtesdecks.jpa.repositories.CryptRepository;
import com.vtesdecks.jpa.repositories.LibraryRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.vtesdecks.service.I18nImageClient.Availability.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CardI18nImagePersistenceTest {
    private LocalContainerEntityManagerFactoryBean factory;
    private JdbcTemplate jdbc;
    private CardI18nImageRepository images;
    private CryptRepository crypt;
    private LibraryRepository library;
    private JpaTransactionManager transactions;

    @BeforeAll
    void database() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE crypt (id INT PRIMARY KEY, modification_date TIMESTAMP)");
        jdbc.execute("CREATE TABLE library (id INT PRIMARY KEY, modification_date TIMESTAMP)");
        jdbc.execute("CREATE TABLE card_i18n_image (card_id INT, locale VARCHAR(2), image VARCHAR(250), "
                + "creation_date TIMESTAMP NOT NULL, modification_date TIMESTAMP NOT NULL, PRIMARY KEY(card_id, locale))");
        factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setPackagesToScan("com.vtesdecks.jpa.entity");
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
        factory.afterPropertiesSet();
        var repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory.getObject()));
        images = repositories.getRepository(CardI18nImageRepository.class);
        crypt = repositories.getRepository(CryptRepository.class);
        library = repositories.getRepository(LibraryRepository.class);
        transactions = new JpaTransactionManager(factory.getObject());
    }

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM card_i18n_image");
        jdbc.update("DELETE FROM crypt");
        jdbc.update("DELETE FROM library");
        jdbc.update("INSERT INTO crypt VALUES (200001, '2020-01-01 00:00:00')");
        jdbc.update("INSERT INTO library VALUES (100001, '2020-01-01 00:00:00')");
    }

    @AfterAll
    void close() {
        factory.destroy();
        jdbc.execute("SHUTDOWN");
    }

    @Test
    void commitsImagesAndCardModificationDatesTogetherAndLeavesUnchangedRowsUntouched() {
        CardI18nImageService service = service(library);
        var present = new I18nImageClient.Check(new CardI18nImageId(200001, "es"), PRESENT);

        assertEquals(new CardI18nImageService.Changes(1, 0, 0, true, false), service.synchronize(List.of(present)));

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM card_i18n_image", Integer.class));
        assertEquals("/img/cards/es/200001.jpg", jdbc.queryForObject("SELECT image FROM card_i18n_image", String.class));
        Timestamp modified = jdbc.queryForObject("SELECT modification_date FROM crypt", Timestamp.class);
        Timestamp imageModified = jdbc.queryForObject("SELECT modification_date FROM card_i18n_image", Timestamp.class);
        assertTrue(modified.after(Timestamp.valueOf("2020-01-01 00:00:00")));
        assertNotNull(imageModified);
        assertEquals(new CardI18nImageService.Changes(0, 0, 0, false, false), service.synchronize(List.of(present)));
        assertEquals(modified, jdbc.queryForObject("SELECT modification_date FROM crypt", Timestamp.class));
        assertEquals(imageModified, jdbc.queryForObject("SELECT modification_date FROM card_i18n_image", Timestamp.class));

        jdbc.update("UPDATE crypt SET modification_date = '2020-01-01 00:00:00'");
        assertEquals(new CardI18nImageService.Changes(0, 0, 1, true, false), service.synchronize(List.of(
                new I18nImageClient.Check(present.id(), MISSING))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM card_i18n_image", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT modification_date FROM crypt", Timestamp.class)
                .after(Timestamp.valueOf("2020-01-01 00:00:00")));
    }

    @Test
    void rollsBackImagesAndTouchedDatesWhenAnyCardUpdateFails() {
        LibraryRepository failingLibrary = mock(LibraryRepository.class);
        when(failingLibrary.selectIds()).thenReturn(List.of(100001));
        doThrow(new IllegalStateException("write failed")).when(failingLibrary).touchModificationDate(anyCollection());
        CardI18nImageService service = service(failingLibrary);

        assertThrows(IllegalStateException.class, () -> service.synchronize(List.of(
                new I18nImageClient.Check(new CardI18nImageId(200001, "es"), PRESENT),
                new I18nImageClient.Check(new CardI18nImageId(100001, "fr"), PRESENT))));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM card_i18n_image", Integer.class));
        assertEquals(Timestamp.valueOf("2020-01-01 00:00:00"),
                jdbc.queryForObject("SELECT modification_date FROM crypt", Timestamp.class));
    }

    private CardI18nImageService service(LibraryRepository libraryRepository) {
        var transaction = new TransactionInterceptor();
        transaction.setTransactionManager(transactions);
        transaction.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(new CardI18nImageService(images, crypt, libraryRepository));
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(transaction);
        return (CardI18nImageService) proxy.getProxy();
    }
}
