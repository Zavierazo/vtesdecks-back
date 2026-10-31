package com.vtesdecks.scheduler.shops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.integration.MarketClient;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import com.vtesdecks.model.ShopPlatform;
import com.vtesdecks.model.market.CardOffer;
import com.vtesdecks.model.market.CardOffersResponse;
import com.vtesdecks.service.CurrencyExchangeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MarketSchedulerTest {
    private final CardShopRepository repository = mock(CardShopRepository.class);
    private final MarketClient client = mock(MarketClient.class);
    private final CurrencyExchangeService exchange = mock(CurrencyExchangeService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final MarketScheduler scheduler = new MarketScheduler(repository, client, mapper, exchange);

    @Test
    void importsVerifiedLanguageVariantsAndReusesLegacyEnglishRows() throws IOException {
        // Reduced snapshot of the live VTES Market response verified on 2026-10-31.
        CardOffersResponse response;
        try (var input = getClass().getResourceAsStream("/market/card-offers.json")) {
            response = mapper.readValue(input, CardOffersResponse.class);
        }
        when(client.getCardOffers(0, 1000)).thenReturn(response);
        when(exchange.convert(new BigDecimal("0.38"), "USD", DEFAULT_CURRENCY)).thenReturn(new BigDecimal("0.35"));
        when(exchange.convert(new BigDecimal("0.48"), "EUR", DEFAULT_CURRENCY)).thenReturn(new BigDecimal("0.48"));
        CardShopEntity dtc = existing(10, ShopPlatform.DTC, null);
        CardShopEntity gp = existing(11, ShopPlatform.GP, null);
        when(repository.findByPlatformIn(anyCollection())).thenReturn(List.of(dtc, gp));

        scheduler.scrapCards();

        ArgumentCaptor<CardShopEntity> saved = ArgumentCaptor.forClass(CardShopEntity.class);
        verify(repository, times(3)).save(saved.capture());
        for (CardOffer offer : response.getOffers()) {
            CardShopEntity actual = saved.getAllValues().stream()
                    .filter(row -> row.getData().get("id").intValue() == offer.getId())
                    .findFirst().orElseThrow();
            assertEquals(offer.getCardId(), actual.getCardId());
            assertEquals(offer.getEdition() + ":" + offer.getEditionDetails(), actual.getSet());
            assertEquals(offer.getLanguage(), actual.getLocale());
            assertEquals(offer.getLink(), actual.getLink());
            assertEquals(offer.getPrice(), actual.getPrice());
            assertEquals(offer.getCurrency(), actual.getCurrency());
            assertTrue(actual.isInStock());
            if ("drivethrucards".equals(offer.getMarket())) {
                assertEquals(ShopPlatform.DTC, actual.getPlatform());
                assertEquals(10, actual.getId());
                assertEquals(new BigDecimal("0.35"), actual.getPriceDefaultCurrency());
            } else {
                assertEquals(ShopPlatform.GP, actual.getPlatform());
                assertEquals(new BigDecimal("0.48"), actual.getPriceDefaultCurrency());
                if ("en".equals(offer.getLanguage())) {
                    assertEquals(11, actual.getId());
                } else {
                    assertNull(actual.getId());
                }
            }
        }
        verify(repository).findByPlatformIn(argThat(platforms -> Set.copyOf(platforms)
                .equals(Set.of(ShopPlatform.DTC, ShopPlatform.GP, ShopPlatform.TCG_MKT, ShopPlatform.EBAY))));
        verify(repository, never()).deleteAll(any());
        verify(repository).flush();
    }

    @Test
    void prefersAnExistingExplicitEnglishRowOverItsLegacyDuplicate() {
        CardOffer offer = offer(1, "gamepod", "en");
        CardShopEntity legacy = existing(10, ShopPlatform.GP, null);
        CardShopEntity english = existing(11, ShopPlatform.GP, "en");
        when(repository.findByPlatformIn(anyCollection())).thenReturn(List.of(legacy, english));
        when(client.getCardOffers(0, 1000)).thenReturn(page(1, List.of(offer)));

        scheduler.scrapCards();

        verify(repository).save(argThat(row -> row.getId().equals(11) && row.getLocale().equals("en")));
        verify(repository).deleteAll(List.of(legacy));
    }

    @Test
    void doesNotReuseAnEnglishLegacyRowForASpanishOffer() {
        CardShopEntity legacy = existing(10, ShopPlatform.GP, null);
        when(repository.findByPlatformIn(anyCollection())).thenReturn(List.of(legacy));
        when(client.getCardOffers(0, 1000)).thenReturn(page(1, List.of(offer(1, "gamepod", "es"))));

        scheduler.scrapCards();

        verify(repository).save(argThat(row -> row.getId() == null && row.getLocale().equals("es")));
        verify(repository).deleteAll(List.of(legacy));
    }

    @Test
    void retainsCheapestOffersForExistingMarketsAndIgnoresUnknownMarkets() {
        CardOffer expensive = offer(1, "tcgmarket", "en");
        expensive.setPrice(new BigDecimal("2.00"));
        CardOffer cheap = offer(2, "tcgmarket", "en");
        cheap.setPrice(new BigDecimal("1.00"));
        cheap.setEdition("Jyhad");
        cheap.setEditionDetails(null);
        expensive.setEdition("Jyhad");
        expensive.setEditionDetails(null);
        CardOffer ebay = offer(3, "ebay", null);
        ebay.setEdition(null);
        ebay.setEditionDetails(null);
        CardOffer unknown = CardOffer.builder().id(4).market("other").build();
        when(client.getCardOffers(0, 1000)).thenReturn(page(4, List.of(expensive, cheap, ebay, unknown)));

        scheduler.scrapCards();

        verify(repository, times(2)).save(any());
        verify(repository).save(argThat(row -> row.getPlatform() == ShopPlatform.TCG_MKT &&
                row.getPrice().equals(new BigDecimal("1.00")) && row.getSet().equals("Jyhad")));
        verify(repository).save(argThat(row -> row.getPlatform() == ShopPlatform.EBAY &&
                row.getSet() == null && row.getLocale() == null));
    }

    @Test
    void readsAllPagesBeforeReconciliation() {
        when(client.getCardOffers(0, 1000)).thenReturn(page(1001, fullPage()));
        when(client.getCardOffers(1000, 1000)).thenAnswer(invocation -> {
            verifyNoInteractions(repository, exchange);
            return page(1001, List.of(offer(1001, "gamepod", "en")));
        });

        scheduler.scrapCards();

        verify(client).getCardOffers(1000, 1000);
        verify(repository).save(any());
        verify(repository).flush();
    }

    @Test
    void keepsTheCheapestOfferForEachSellerPrintingAndLanguage() {
        CardOffer expensive = offer(1, "ebay", "en");
        expensive.setSeller("seller-one");
        expensive.setPrice(new BigDecimal("2.00"));
        CardOffer cheap = offer(2, "ebay", "en");
        cheap.setSeller("seller-one");
        cheap.setCountry("GB");
        cheap.setContinent("EU");
        cheap.setPrice(new BigDecimal("1.00"));
        CardOffer secondSeller = offer(3, "ebay", "en");
        secondSeller.setSeller("seller-two");
        CardOffer spanish = offer(4, "ebay", "es");
        spanish.setSeller("seller-one");
        CardOffer otherPrinting = offer(5, "ebay", "en");
        otherPrinting.setSeller("seller-one");
        otherPrinting.setEdition("Jyhad");
        otherPrinting.setEditionDetails(null);
        when(client.getCardOffers(0, 1000)).thenReturn(page(5, List.of(expensive, cheap, secondSeller, spanish, otherPrinting)));

        scheduler.scrapCards();

        verify(repository, times(4)).save(any());
        verify(repository, never()).save(argThat(row -> row.getData().get("id").intValue() == 1));
        verify(repository).save(argThat(row -> row.getData().get("id").intValue() == 2 &&
                row.getSeller().equals("seller-one") && row.getCountry().equals("GB") && row.getContinent().equals("EU")));
        verify(repository).save(argThat(row -> row.getSeller().equals("seller-two")));
        verify(repository).save(argThat(row -> row.getLocale().equals("es")));
        verify(repository).save(argThat(row -> row.getSet().equals("Jyhad")));
    }

    @Test
    void importsThePublishedSellerPayloadAndKeepsBothVendors() throws IOException {
        // Reduced live response captured after seller was published on 2026-10-31.
        CardOffersResponse response;
        try (var input = getClass().getResourceAsStream("/market/seller-offers.json")) {
            response = mapper.readValue(input, CardOffersResponse.class);
        }
        when(client.getCardOffers(0, 1000)).thenReturn(response);

        scheduler.scrapCards();

        ArgumentCaptor<CardShopEntity> saved = ArgumentCaptor.forClass(CardShopEntity.class);
        verify(repository, times(2)).save(saved.capture());
        assertEquals(Set.of("idasoft17", "melvin-lightfoot"),
                Set.copyOf(saved.getAllValues().stream().map(CardShopEntity::getSeller).toList()));
        for (CardShopEntity row : saved.getAllValues()) {
            assertEquals(101774, row.getCardId());
            assertNull(row.getSet());
            assertEquals("en", row.getLocale());
            assertEquals(new BigDecimal("2.0"), row.getPrice());
            assertEquals("USD", row.getCurrency());
            assertEquals("idasoft17".equals(row.getSeller()) ? "AU" : "NO", row.getCountry());
            assertEquals("idasoft17".equals(row.getSeller()) ? "OC" : "EU", row.getContinent());
        }
    }

    @Test
    void updatesLocationWithoutChangingTheOfferIdentityAndIsIdempotent() {
        CardOffer offer = offer(1, "ebay", "en");
        offer.setSeller("seller-one");
        offer.setCountry("FR");
        offer.setContinent("EU");
        CardShopEntity current = existing(10, ShopPlatform.EBAY, "en");
        current.setSet("POD:GP");
        current.setSeller("seller-one");
        current.setLink(offer.getLink());
        current.setPrice(offer.getPrice());
        current.setCountry("US");
        current.setContinent("NA");
        when(client.getCardOffers(0, 1000)).thenReturn(page(1, List.of(offer)));
        when(repository.findByPlatformIn(anyCollection())).thenReturn(List.of(current));

        scheduler.scrapCards();

        ArgumentCaptor<CardShopEntity> saved = ArgumentCaptor.forClass(CardShopEntity.class);
        verify(repository).save(saved.capture());
        assertEquals(10, saved.getValue().getId());
        assertEquals("FR", saved.getValue().getCountry());
        assertEquals("EU", saved.getValue().getContinent());
        verify(repository, never()).deleteAll(any());

        when(repository.findByPlatformIn(anyCollection())).thenReturn(List.of(saved.getValue()));
        scheduler.scrapCards();

        verify(repository, times(1)).save(any());
        verify(repository, never()).deleteAll(any());
    }

    @Test
    void treatsBlankAndMissingSellersAsOneNullableSeller() {
        CardOffer blank = offer(1, "ebay", null);
        blank.setSeller(" ");
        blank.setEdition(null);
        blank.setEditionDetails("ignored without an edition");
        blank.setPrice(new BigDecimal("2.00"));
        CardOffer absent = offer(2, "ebay", null);
        absent.setEdition(null);
        absent.setEditionDetails(null);
        when(client.getCardOffers(0, 1000)).thenReturn(page(2, List.of(blank, absent)));

        scheduler.scrapCards();

        verify(repository).save(argThat(row -> row.getSeller() == null && row.getSet() == null &&
                row.getLocale() == null && row.getData().get("id").intValue() == 2));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidFirstPages")
    void rejectsMalformedOrIncompleteDownloadsBeforeAccessingTheDatabase(String name, CardOffersResponse response) {
        when(client.getCardOffers(0, 1000)).thenReturn(response);

        assertThrows(IllegalStateException.class, scheduler::scrapCards);

        verifyNoInteractions(repository, exchange);
    }

    static Stream<Arguments> invalidFirstPages() {
        return Stream.of(
                Arguments.of("null response", null),
                Arguments.of("missing total", CardOffersResponse.builder().offers(List.of()).build()),
                Arguments.of("negative total", page(-1, List.of())),
                Arguments.of("missing offers", page(1, null)),
                Arguments.of("empty incomplete page", page(1, List.of())),
                Arguments.of("short incomplete page", page(2, List.of(offer(1, "gamepod", "en")))),
                Arguments.of("more offers than total", page(0, List.of(offer(1, "gamepod", "en")))),
                Arguments.of("oversized page", page(1001, IntStream.rangeClosed(1, 1001)
                        .mapToObj(id -> offer(id, "gamepod", "en")).toList())),
                Arguments.of("duplicate ID", page(2, List.of(offer(1, "gamepod", "en"), offer(1, "gamepod", "es")))),
                Arguments.of("null offer", page(1, Arrays.asList((CardOffer) null))),
                Arguments.of("missing offer ID", page(1, List.of(CardOffer.builder().market("gamepod").build())))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSecondPages")
    void retainsExistingOffersWhenALaterPageIsIncomplete(String name, CardOffersResponse response) {
        when(client.getCardOffers(0, 1000)).thenReturn(page(1001, fullPage()));
        when(client.getCardOffers(1000, 1000)).thenReturn(response);

        assertThrows(IllegalStateException.class, scheduler::scrapCards);

        verifyNoInteractions(repository, exchange);
    }

    static Stream<Arguments> invalidSecondPages() {
        return Stream.of(
                Arguments.of("null later response", null),
                Arguments.of("empty later page", page(1001, List.of())),
                Arguments.of("changed total", page(1002, List.of(offer(1001, "gamepod", "en")))),
                Arguments.of("repeated page", page(1001, List.of(offer(1, "gamepod", "en"))))
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1000})
    void propagatesDownloadFailuresWithoutDatabaseWrites(int offset) {
        when(client.getCardOffers(0, 1000)).thenReturn(page(1001, fullPage()));
        when(client.getCardOffers(offset, 1000)).thenThrow(new IllegalStateException("Unavailable market"));

        assertThrows(IllegalStateException.class, scheduler::scrapCards);

        verifyNoInteractions(repository, exchange);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidOffers")
    void rejectsInvalidSupportedOffersWithoutSavingOrDeleting(String field, CardOffer invalid) {
        when(client.getCardOffers(0, 1000)).thenReturn(page(2, List.of(offer(1, "gamepod", "en"), invalid)));

        assertThrows(IllegalArgumentException.class, scheduler::scrapCards);

        verify(repository, never()).save(any());
        verify(repository, never()).deleteAll(any());
        verify(repository, never()).flush();
        verifyNoInteractions(exchange);
    }

    static Stream<Arguments> invalidOffers() {
        CardOffer cardId = offer(2, "drivethrucards", "en");
        cardId.setCardId(null);
        CardOffer link = offer(2, "drivethrucards", "en");
        link.setLink(null);
        CardOffer emptyLink = offer(2, "drivethrucards", "en");
        emptyLink.setLink(" ");
        CardOffer price = offer(2, "drivethrucards", "en");
        price.setPrice(null);
        CardOffer currency = offer(2, "drivethrucards", "en");
        currency.setCurrency(null);
        CardOffer market = offer(2, "drivethrucards", "en");
        market.setMarket(null);
        return Stream.of(Arguments.of("card ID", cardId), Arguments.of("link", link),
                Arguments.of("empty link", emptyLink), Arguments.of("price", price),
                Arguments.of("currency", currency), Arguments.of("market", market));
    }

    @Test
    void doesNotDeleteStaleOffersAfterASaveFailure() {
        when(client.getCardOffers(0, 1000)).thenReturn(page(1, List.of(offer(1, "gamepod", "en"))));
        when(repository.findByPlatformIn(anyCollection())).thenReturn(List.of(existing(10, ShopPlatform.DTC, null)));
        when(repository.save(any())).thenThrow(new IllegalStateException("Database failure"));

        assertThrows(IllegalStateException.class, scheduler::scrapCards);

        verify(repository, never()).deleteAll(any());
        verify(repository, never()).flush();
    }

    @Test
    void doesNotRewriteUnchangedOffers() {
        CardOffer offer = offer(1, "gamepod", "en");
        CardShopEntity current = existing(10, ShopPlatform.GP, "en");
        current.setLink(offer.getLink());
        current.setPrice(offer.getPrice());
        when(client.getCardOffers(0, 1000)).thenReturn(page(1, List.of(offer)));
        when(repository.findByPlatformIn(anyCollection())).thenReturn(List.of(current));

        scheduler.scrapCards();

        verify(repository, never()).save(any());
        verify(repository, never()).deleteAll(any());
        verify(repository).flush();
    }

    private static List<CardOffer> fullPage() {
        return IntStream.rangeClosed(1, 1000).mapToObj(id -> offer(id, "gamepod", "en")).toList();
    }

    private static CardOffersResponse page(long total, List<CardOffer> offers) {
        return CardOffersResponse.builder().total(total).offers(offers).build();
    }

    private static CardOffer offer(int id, String market, String locale) {
        boolean dtc = "drivethrucards".equals(market);
        return CardOffer.builder().id(id).cardId(200035).market(market).language(locale)
                .edition("POD").editionDetails(dtc ? "DTC" : "GP")
                .link("https://example.com/offers/" + id)
                .price(new BigDecimal(dtc ? "0.38" : "0.48")).currency(dtc ? "USD" : "EUR").build();
    }

    private static CardShopEntity existing(int id, ShopPlatform platform, String locale) {
        return CardShopEntity.builder().id(id).cardId(200035).platform(platform)
                .set("POD:" + platform).locale(locale).link("https://example.com/previous")
                .price(new BigDecimal("0.35")).currency(platform == ShopPlatform.DTC ? "USD" : "EUR")
                .inStock(true).build();
    }
}
