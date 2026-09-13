package com.vtesdecks.api.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.cqengine.attribute.Attribute;
import com.googlecode.cqengine.query.option.QueryOptions;
import com.vtesdecks.api.service.ApiCollectionService;
import com.vtesdecks.api.service.ApiReactionService;
import com.vtesdecks.cache.DeckCacheFixtures;
import com.vtesdecks.cache.DeckCardIndex;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.DeckCard;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.CollectionTracker;
import com.vtesdecks.configuration.WebConfiguration;
import com.vtesdecks.model.api.ApiDeck;
import com.vtesdecks.service.CurrencyExchangeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.redis.core.convert.MappingRedisConverter;
import org.springframework.data.redis.core.convert.RedisData;
import org.springframework.data.redis.core.mapping.RedisMappingContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiDeckSummaryMapperTest {
    private ApiDeckMapper mapper;
    private Deck full;
    private ObjectMapper json;
    private ApiCollectionService collection;

    @BeforeEach
    void setUp() throws Exception {
        full = DeckCacheFixtures.deck();
        json = new WebConfiguration().jacksonBuilder().build();
        mapper = Mappers.getMapper(ApiDeckMapper.class);
        ReflectionTestUtils.setField(mapper, "apiPublicUserMapper", Mappers.getMapper(ApiPublicUserMapper.class));
        var errataMapper = Mappers.getMapper(ApiCardErrataMapper.class);
        ReflectionTestUtils.setField(mapper, "apiCardErrataMapper", errataMapper);
        ReflectionTestUtils.setField(mapper, "apiReactionService", mock(ApiReactionService.class));
        collection = mock(ApiCollectionService.class);
        ReflectionTestUtils.setField(mapper, "apiCollectionService", collection);
        LibraryCache library = mock(LibraryCache.class);
        Library card = new Library();
        card.setType("Action/Combat");
        when(library.get(100001)).thenReturn(card);
        ReflectionTestUtils.setField(mapper, "libraryCache", library);
        ReflectionTestUtils.setField(errataMapper, "libraryCache", library);
        DeckCardIndex cards = new DeckCardIndex();
        cards.setUp();
        cards.getRepository().add(DeckCard.builder().deckId(full.getId()).id(200001).number(4).build());
        cards.getRepository().add(DeckCard.builder().deckId(full.getId()).id(100001).number(8).build());
        ReflectionTestUtils.setField(mapper, "deckCardIndex", cards);
        CurrencyExchangeService currency = mock(CurrencyExchangeService.class);
        when(currency.convert(any(), anyString(), eq("USD"))).thenReturn(new BigDecimal("30.00"));
        ReflectionTestUtils.setField(mapper, "currencyExchangeService", currency);
    }

    @Test
    void cachedDetailPreservesEmptyDisciplineAndClanArrays() {
        full.getStats().getLibraryDisciplines().getFirst().setDisciplines(Set.of());
        full.getStats().getLibraryClans().getFirst().setClans(Set.of());
        var converter = new MappingRedisConverter(new RedisMappingContext());
        converter.afterPropertiesSet();
        RedisData data = new RedisData();
        converter.write(full, data);
        Deck cached = converter.read(Deck.class, data);

        var response = json.valueToTree(mapper.map(cached, null, false, "EUR"));
        assertTrue(response.at("/stats/libraryDisciplines/0/disciplines").isArray());
        assertTrue(response.at("/stats/libraryClans/0/clans").isArray());
        assertEquals(json.valueToTree(mapper.map(full, null, false, "EUR")), response);
    }

    @Test
    void summaryMatchesFullModelSummaryIncludingAdventAndFilteredQuantities() {
        var filter = Map.of(200001, 1, 100001, 1, 100002, 1);
        ApiDeck expected = mapper.mapSummary(full, 42, filter, "USD");
        ApiDeck actual = mapper.mapSummary(DeckSummary.from(full), 42, filter, "USD");
        assertEquals(json.valueToTree(expected), json.valueToTree(actual));
        assertEquals(full.getExtra(), actual.getExtra());
        assertEquals(Map.of(200001, 4, 100001, 8, 100002, 0), actual.getFilterCards().stream()
                .collect(Collectors.toMap(card -> card.getId(), card -> card.getNumber())));
        assertTrue(actual.getOwner());
        assertTrue(actual.getFavorite());
        assertNull(actual.getDescription());
        assertNull(actual.getStats().getCryptDisciplines());
        assertNull(actual.getStats().getLibraryClans());
        assertNull(actual.getCrypt());
        assertNull(actual.getLibrary());
    }

    @Test
    void ordinaryExtrasStayOutOfResidentSummary() throws Exception {
        full.setExtra(json.readTree("{\"notes\":\"detail only\"}"));
        DeckSummary summary = DeckSummary.from(full);
        assertNull(summary.getExtra());
        assertNull(mapper.mapSummary(summary, null, null, "EUR").getExtra());
        assertNotNull(mapper.map(full, null, false, "EUR").getExtra());
    }

    @Test
    void detailsPreserveStatisticsWarningsAndPerRequestCollectionAndCurrency() {
        when(collection.getCollectionCardsMap()).thenReturn(Map.of(200001, 2, 100001, 8));
        ApiDeck owned = mapper.map(full, 42, true, "USD");
        ApiDeck anonymous = mapper.map(full, null, false, "EUR");
        assertEquals(CollectionTracker.PARTIAL, owned.getCrypt().getFirst().getCollection());
        assertEquals(CollectionTracker.FULL, owned.getLibrary().getFirst().getCollection());
        assertEquals(new BigDecimal("30.00"), owned.getStats().getPrice());
        assertEquals(new BigDecimal("25.50"), anonymous.getStats().getPrice());
        assertFalse(anonymous.getOwner());
        assertFalse(anonymous.getFavorite());
        assertNull(anonymous.getCrypt().getFirst().getCollection());
        assertEquals(full.getDescription(), anonymous.getDescription());
        assertEquals(1, anonymous.getStats().getCryptDisciplines().size());
        assertEquals(1, anonymous.getStats().getLibraryDisciplines().size());
        assertEquals(1, anonymous.getStats().getLibraryClans().size());
        assertEquals(1, anonymous.getWarnings().size());
        assertEquals(1, anonymous.getErratas().size());
        assertEquals(2, anonymous.getBookmarks());
    }

    @Test
    @SuppressWarnings("unchecked")
    void allSearchAttributesHaveIdenticalValuesAfterProjection() throws Exception {
        DeckSummary summary = DeckSummary.from(full);
        for (var field : DeckSummary.class.getFields()) {
            if (Attribute.class.isAssignableFrom(field.getType())) {
                Attribute<DeckSummary, ?> attribute = (Attribute<DeckSummary, ?>) field.get(null);
                assertEquals(StreamSupport.stream(attribute.getValues(full, new QueryOptions()).spliterator(), false).toList(),
                        StreamSupport.stream(attribute.getValues(summary, new QueryOptions()).spliterator(), false).toList(), field.getName());
            }
        }
    }
}
