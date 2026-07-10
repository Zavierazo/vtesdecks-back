package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.api.mapper.ApiCollectionMapper;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.jpa.entity.CollectionCardEntity;
import com.vtesdecks.jpa.entity.CollectionEntity;
import com.vtesdecks.jpa.repositories.CollectionBinderRepository;
import com.vtesdecks.jpa.repositories.CollectionCardHistoryRepository;
import com.vtesdecks.jpa.repositories.CollectionCardRepository;
import com.vtesdecks.jpa.repositories.CollectionCardRepositoryCustom;
import com.vtesdecks.jpa.repositories.CollectionRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.api.ApiCollectionCardStats;
import com.vtesdecks.service.DeckService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApiCollectionServiceTest {
    private static final int USER_ID = 42;
    private static final int COLLECTION_ID = 7;
    private static final int CRYPT_ID = 200001;
    private static final int LIBRARY_ID = 100001;
    private static final int OTHER_LIBRARY_ID = 100002;

    @Mock
    private UserRepository userRepository;
    @Mock
    private DeckService deckService;
    @Mock
    private CollectionRepository collectionRepository;
    @Mock
    private CollectionBinderRepository collectionBinderRepository;
    @Mock
    private CollectionCardRepository collectionCardRepository;
    @Mock
    private CollectionCardRepositoryCustom collectionCardRepositoryCustom;
    @Mock
    private CollectionCardHistoryRepository collectionCardHistoryRepository;
    @Mock
    private ApiCollectionMapper apiCollectionMapper;
    @Mock
    private ApiCollectionImportService apiCollectionImportService;
    @InjectMocks
    private ApiCollectionService service;

    @BeforeEach
    public void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(USER_ID), null, List.of()));
        CollectionEntity collection = new CollectionEntity();
        collection.setId(COLLECTION_ID);
        collection.setUserId(USER_ID);
        lenient().when(collectionRepository.findByUserIdAndDeletedFalse(USER_ID)).thenReturn(List.of(collection));
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void shouldReturnStatsForEveryRequestedCard() throws Exception {
        when(collectionCardRepository.findByCollectionIdAndCardIdIn(eq(COLLECTION_ID), anyList()))
                .thenReturn(List.of(
                        collectionCard(CRYPT_ID, 2),
                        collectionCard(CRYPT_ID, 1),
                        collectionCard(LIBRARY_ID, 4)));
        mockDecks(
                deck("deck1", true, Map.of(CRYPT_ID, 2, LIBRARY_ID, 1)),
                deck("deck2", false, Map.of(CRYPT_ID, 1)));

        List<ApiCollectionCardStats> stats = service.getCardStatsBulk(List.of(CRYPT_ID, LIBRARY_ID, OTHER_LIBRARY_ID));

        assertEquals(3, stats.size());
        assertStats(stats.get(0), CRYPT_ID, 3, 3, 2);
        assertStats(stats.get(1), LIBRARY_ID, 4, 1, 1);
        assertStats(stats.get(2), OTHER_LIBRARY_ID, 0, 0, 0);
    }

    @Test
    public void shouldQueryOnlyUserDecks() throws Exception {
        when(collectionCardRepository.findByCollectionIdAndCardIdIn(eq(COLLECTION_ID), anyList()))
                .thenReturn(List.of());
        mockDecks();

        service.getCardStatsBulk(List.of(CRYPT_ID));

        ArgumentCaptor<DeckQuery> queryCaptor = ArgumentCaptor.forClass(DeckQuery.class);
        verify(deckService).getDecks(queryCaptor.capture());
        assertEquals(DeckType.USER, queryCaptor.getValue().getType());
        assertEquals(Integer.valueOf(USER_ID), queryCaptor.getValue().getUserId());
    }

    @Test
    public void shouldDeduplicateRequestedCardIds() throws Exception {
        when(collectionCardRepository.findByCollectionIdAndCardIdIn(eq(COLLECTION_ID), anyList()))
                .thenReturn(List.of(collectionCard(CRYPT_ID, 1)));
        mockDecks();

        List<ApiCollectionCardStats> stats = service.getCardStatsBulk(List.of(CRYPT_ID, CRYPT_ID));

        assertEquals(1, stats.size());
        assertStats(stats.get(0), CRYPT_ID, 1, 0, 0);
    }

    @Test
    public void shouldLimitDeckNumbersToTenNewestDecks() throws Exception {
        when(collectionCardRepository.findByCollectionIdAndCardIdIn(eq(COLLECTION_ID), anyList()))
                .thenReturn(List.of());
        // Same 10-deck window as the single-card stats endpoint
        Deck[] decks = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> deck("deck" + i, true, Map.of(CRYPT_ID, 1)))
                .toArray(Deck[]::new);
        mockDecks(decks);

        List<ApiCollectionCardStats> stats = service.getCardStatsBulk(List.of(CRYPT_ID));

        assertStats(stats.get(0), CRYPT_ID, 0, 10, 10);
    }

    @Test
    public void shouldRejectTooManyCardIds() {
        List<Integer> cardIds = IntStream.rangeClosed(1, 501).boxed().toList();

        assertThrows(IllegalArgumentException.class, () -> service.getCardStatsBulk(cardIds));
    }

    private void mockDecks(Deck... decks) {
        @SuppressWarnings("unchecked")
        ResultSet<Deck> resultSet = mock(ResultSet.class);
        when(resultSet.stream()).thenAnswer(invocation -> Stream.of(decks));
        when(deckService.getDecks(any(DeckQuery.class))).thenReturn(resultSet);
    }

    private Deck deck(String id, boolean tracked, Map<Integer, Integer> cards) {
        Deck deck = new Deck();
        deck.setId(id);
        deck.setName(id);
        deck.setCollection(tracked);
        cards.forEach((cardId, number) -> {
            Card card = Card.builder().id(cardId).number(number).build();
            if (cardId >= 200000) {
                deck.getCrypt().add(card);
            } else {
                deck.getLibraryByType().computeIfAbsent("Master", k -> new ArrayList<>()).add(card);
            }
        });
        return deck;
    }

    private CollectionCardEntity collectionCard(int cardId, int number) {
        CollectionCardEntity card = new CollectionCardEntity();
        card.setCollectionId(COLLECTION_ID);
        card.setCardId(cardId);
        card.setNumber(number);
        return card;
    }

    private void assertStats(ApiCollectionCardStats stats, int cardId, int collectionNumber, int decksNumber, int trackedDecksNumber) {
        assertEquals(Integer.valueOf(cardId), stats.getCardId());
        assertEquals(Integer.valueOf(collectionNumber), stats.getCollectionNumber());
        assertEquals(Integer.valueOf(decksNumber), stats.getDecksNumber());
        assertEquals(Integer.valueOf(trackedDecksNumber), stats.getTrackedDecksNumber());
    }
}
