package com.vtesdecks.scheduler.shops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.integration.MarketClient;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import com.vtesdecks.model.ShopPlatform;
import com.vtesdecks.model.market.CardOffer;
import com.vtesdecks.model.market.CardOffersResponse;
import com.vtesdecks.service.CurrencyExchangeService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MarketSchedulerTransactionTest {
    @ParameterizedTest
    @ValueSource(strings = {"save", "delete", "flush"})
    void rollsBackChangesAndCleanupWhenPersistenceFails(String failure) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try {
            jdbc.execute("CREATE TABLE card_shop (id INT PRIMARY KEY, card_id INT, platform VARCHAR(16), locale VARCHAR(10), price DECIMAL(10,2))");
            jdbc.update("INSERT INTO card_shop VALUES (10, 200035, 'GP', NULL, 0.35), (11, 200001, 'DTC', NULL, 0.38)");
            CardShopRepository repository = mock(CardShopRepository.class);
            MarketClient client = mock(MarketClient.class);
            when(repository.findByPlatformIn(anyCollection())).thenAnswer(invocation ->
                    jdbc.query("SELECT * FROM card_shop", (rs, row) -> {
                        ShopPlatform platform = ShopPlatform.valueOf(rs.getString("platform"));
                        return CardShopEntity.builder().id(rs.getInt("id")).cardId(rs.getInt("card_id"))
                                .platform(platform).set("POD:" + platform).locale(rs.getString("locale"))
                                .link("https://example.com/previous").price(rs.getBigDecimal("price"))
                                .currency(platform == ShopPlatform.GP ? "EUR" : "USD").inStock(true).build();
                    }));
            when(repository.save(any(CardShopEntity.class))).thenAnswer(invocation -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                CardShopEntity row = invocation.getArgument(0);
                jdbc.update("UPDATE card_shop SET price = ?, locale = ? WHERE id = ?", row.getPrice(), row.getLocale(), row.getId());
                if ("save".equals(failure)) {
                    throw new IllegalStateException("Failure after updating an offer");
                }
                return row;
            });
            doAnswer(invocation -> {
                Iterable<CardShopEntity> rows = invocation.getArgument(0);
                for (CardShopEntity row : rows) {
                    jdbc.update("DELETE FROM card_shop WHERE id = ?", row.getId());
                }
                if ("delete".equals(failure)) {
                    throw new IllegalStateException("Failure after deleting a stale offer");
                }
                return null;
            }).when(repository).deleteAll(any());
            doAnswer(invocation -> {
                if ("flush".equals(failure)) {
                    throw new IllegalStateException("Failure while flushing the import");
                }
                return null;
            }).when(repository).flush();
            CardOffer offer = CardOffer.builder().id(119067).cardId(200035).market("gamepod")
                    .language("en").edition("POD").editionDetails("GP")
                    .link("https://example.com/current").price(new BigDecimal("0.48")).currency("EUR").build();
            when(client.getCardOffers(0, 1000)).thenReturn(CardOffersResponse.builder().total(1L).offers(List.of(offer)).build());
            MarketScheduler target = new MarketScheduler(repository, client, new ObjectMapper(), mock(CurrencyExchangeService.class));
            TransactionInterceptor transaction = new TransactionInterceptor();
            transaction.setTransactionManager(new DataSourceTransactionManager(dataSource));
            transaction.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            ProxyFactory factory = new ProxyFactory(target);
            factory.setProxyTargetClass(true);
            factory.addAdvice(transaction);
            MarketScheduler scheduler = (MarketScheduler) factory.getProxy();

            assertThrows(IllegalStateException.class, scheduler::scrapCards);

            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM card_shop", Integer.class));
            assertEquals(new BigDecimal("0.35"), jdbc.queryForObject("SELECT price FROM card_shop WHERE id = 10", BigDecimal.class));
            assertNull(jdbc.queryForObject("SELECT locale FROM card_shop WHERE id = 10", String.class));
            assertEquals(new BigDecimal("0.38"), jdbc.queryForObject("SELECT price FROM card_shop WHERE id = 11", BigDecimal.class));
            verify(repository).save(argThat(row -> row.getId().equals(10)));
            if ("save".equals(failure)) {
                verify(repository, never()).deleteAll(any());
                verify(repository, never()).flush();
            } else {
                verify(repository).deleteAll(argThat(rows -> rows.iterator().next().getId().equals(11)));
                if ("flush".equals(failure)) {
                    verify(repository).flush();
                }
            }
        } finally {
            jdbc.execute("SHUTDOWN");
        }
    }
}
