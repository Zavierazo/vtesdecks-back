package com.vtesdecks.scheduler.tournament;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckCardEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.model.twda.TwdaCard;
import com.vtesdecks.model.twda.TwdaDeck;
import com.vtesdecks.model.twda.TwdaEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TournamentDeckSchedulerTest {
    private static final LocalDate DATE = LocalDate.of(2023, 5, 13);
    private static final int CRYPT_ID = 200001;
    private static final int LIBRARY_ID = 100001;
    private static final int OTHER_LIBRARY_ID = 100002;

    @Mock
    private DeckRepository deckRepository;
    @Mock
    private DeckCardRepository deckCardRepository;
    @Mock
    private CryptCache cryptCache;
    @Mock
    private LibraryCache libraryCache;
    @Mock
    private PlatformTransactionManager transactionManager;
    @InjectMocks
    private TournamentDeckScheduler scheduler;

    @BeforeEach
    public void setUp() {
        scheduler.setUp();
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(deckRepository.findById(any())).thenReturn(Optional.empty());
        when(deckRepository.findByTypeAndNameContainingIgnoreCase(any(), any())).thenReturn(Collections.emptyList());
        when(deckCardRepository.findByIdDeckId(any())).thenReturn(Collections.emptyList());
        when(cryptCache.get(any())).thenReturn(new Crypt());
        when(libraryCache.get(any())).thenReturn(new Library());
    }

    @Test
    public void shouldInsertNewDeckWithMappedFields() {
        scheduler.parseDeck(twdaDeck());

        ArgumentCaptor<DeckEntity> deckCaptor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(deckCaptor.capture());
        DeckEntity deck = deckCaptor.getValue();
        assertEquals("tournament-2023event", deck.getId());
        assertEquals(DeckType.TOURNAMENT, deck.getType());
        assertEquals("http://www.vekn.fr/decks/twd.htm#2023event", deck.getSource());
        assertEquals("My Event 2023", deck.getTournament());
        assertEquals(20, deck.getPlayers());
        assertEquals(2023, deck.getYear());
        assertEquals("John Doe", deck.getAuthor());
        assertEquals("https://example.org/event", deck.getUrl());
        assertEquals("My Deck", deck.getName());
        assertEquals("First line\nSecond line", deck.getDescription());
        assertEquals(DATE.atStartOfDay(), deck.getCreationDate());
        assertEquals(false, deck.getVerified());

        ArgumentCaptor<DeckCardEntity> cardCaptor = ArgumentCaptor.forClass(DeckCardEntity.class);
        verify(deckCardRepository, times(2)).saveAndFlush(cardCaptor.capture());
        List<DeckCardEntity> cards = cardCaptor.getAllValues();
        assertEquals(12, cards.stream().filter(card -> card.getId().getCardId() == CRYPT_ID).findFirst().orElseThrow().getNumber());
        assertEquals(60, cards.stream().filter(card -> card.getId().getCardId() == LIBRARY_ID).findFirst().orElseThrow().getNumber());
    }

    @Test
    public void shouldUpdateCardsOfExistingUnverifiedDeck() {
        DeckEntity actual = existingDeck(false);
        when(deckRepository.findById("tournament-2023event")).thenReturn(Optional.of(actual));
        when(deckCardRepository.findByIdDeckId("tournament-2023event")).thenReturn(List.of(
                deckCard(CRYPT_ID, 12),
                deckCard(LIBRARY_ID, 59),
                deckCard(OTHER_LIBRARY_ID, 1)));

        scheduler.parseDeck(twdaDeck());

        ArgumentCaptor<DeckCardEntity> cardCaptor = ArgumentCaptor.forClass(DeckCardEntity.class);
        verify(deckCardRepository).saveAndFlush(cardCaptor.capture());
        assertEquals(LIBRARY_ID, cardCaptor.getValue().getId().getCardId());
        assertEquals(60, cardCaptor.getValue().getNumber());
        verify(deckCardRepository).deleteById(deckCard(OTHER_LIBRARY_ID, 1).getId());
    }

    @Test
    public void shouldNeverModifyVerifiedDeck() {
        DeckEntity actual = existingDeck(true);
        actual.setName("Curated name");
        when(deckRepository.findById("tournament-2023event")).thenReturn(Optional.of(actual));
        when(deckCardRepository.findByIdDeckId("tournament-2023event")).thenReturn(List.of(
                deckCard(CRYPT_ID, 12),
                deckCard(LIBRARY_ID, 59),
                deckCard(OTHER_LIBRARY_ID, 1)));

        scheduler.parseDeck(twdaDeck());

        verify(deckRepository, never()).saveAndFlush(any());
        verify(deckCardRepository, never()).saveAndFlush(any());
        verify(deckCardRepository, never()).deleteById(any());
    }

    @Test
    public void shouldMergeDuplicateCardEntries() {
        TwdaDeck source = twdaDeck();
        card(source, LIBRARY_ID).setCount(50);
        source.getCards().add(twdaCard(LIBRARY_ID, 10));

        scheduler.parseDeck(source);

        ArgumentCaptor<DeckCardEntity> cardCaptor = ArgumentCaptor.forClass(DeckCardEntity.class);
        verify(deckCardRepository, times(2)).saveAndFlush(cardCaptor.capture());
        assertEquals(60, cardCaptor.getAllValues().stream()
                .filter(card -> card.getId().getCardId() == LIBRARY_ID).findFirst().orElseThrow().getNumber());
    }

    @Test
    public void shouldSkipDeckWithUnknownCard() {
        when(libraryCache.get(LIBRARY_ID)).thenReturn(null);

        scheduler.parseDeck(twdaDeck());

        verify(deckRepository, never()).saveAndFlush(any());
        verify(deckCardRepository, never()).saveAndFlush(any());
    }

    @Test
    public void shouldBeLenientWithOldDeckSizes() {
        TwdaDeck source = twdaDeck();
        source.getEvent().setDate(LocalDate.of(2010, 5, 13));
        card(source, CRYPT_ID).setCount(11);
        card(source, LIBRARY_ID).setCount(91);

        scheduler.parseDeck(source);

        verify(deckRepository).saveAndFlush(any());
    }

    @Test
    public void shouldRejectModernDeckWithIllegalSizes() {
        TwdaDeck source = twdaDeck();
        card(source, CRYPT_ID).setCount(11);

        scheduler.parseDeck(source);

        verify(deckRepository, never()).saveAndFlush(any());
    }

    @Test
    public void shouldPreserveCreationDateWhenOnlyTimeDiffers() {
        DeckEntity actual = existingDeck(false);
        when(deckRepository.findById("tournament-2023event")).thenReturn(Optional.of(actual));
        when(deckCardRepository.findByIdDeckId("tournament-2023event")).thenReturn(List.of(
                deckCard(CRYPT_ID, 12),
                deckCard(LIBRARY_ID, 60)));

        scheduler.parseDeck(twdaDeck());

        //Nothing changed besides the time of day, so no update at all must happen
        verify(deckRepository, never()).saveAndFlush(any());
        verify(deckCardRepository, never()).saveAndFlush(any());
    }

    @Test
    public void shouldFallbackDeckNameWhenMissing() {
        TwdaDeck source = twdaDeck();
        source.setName(null);

        scheduler.parseDeck(source);

        ArgumentCaptor<DeckEntity> deckCaptor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(deckCaptor.capture());
        assertEquals("John Doe, My Event 2023, 2023", deckCaptor.getValue().getName());
    }

    @Test
    public void shouldKeepExistingNameWhenArchiveHasNone() {
        DeckEntity actual = existingDeck(false);
        actual.setName("Original scraped name");
        //Unrelated metadata change so the update is persisted and the name can be captured
        actual.setPlayers(10);
        when(deckRepository.findById("tournament-2023event")).thenReturn(Optional.of(actual));
        when(deckCardRepository.findByIdDeckId("tournament-2023event")).thenReturn(List.of(
                deckCard(CRYPT_ID, 12),
                deckCard(LIBRARY_ID, 60)));
        TwdaDeck source = twdaDeck();
        source.setName(null);

        scheduler.parseDeck(source);

        ArgumentCaptor<DeckEntity> deckCaptor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(deckCaptor.capture());
        assertEquals("Original scraped name", deckCaptor.getValue().getName());
    }

    @Test
    public void shouldSkipDuplicateOfDeckImportedUnderAnotherId() {
        DeckEntity other = existingDeck(false);
        other.setId("tournament-99999");
        when(deckRepository.findByTypeAndNameContainingIgnoreCase(DeckType.TOURNAMENT, "My Deck")).thenReturn(List.of(other));

        scheduler.parseDeck(twdaDeck());

        verify(deckRepository, never()).saveAndFlush(any());
        verify(deckCardRepository, never()).saveAndFlush(any());
    }

    @Test
    public void shouldStripDescriptionLabelAndIndentation() {
        TwdaDeck source = twdaDeck();
        source.setComment("  Description:  First line \r\n   Second line\r\n\nNew paragraph\r");

        scheduler.parseDeck(source);

        ArgumentCaptor<DeckEntity> deckCaptor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(deckCaptor.capture());
        assertEquals("First line\r\nSecond line\r\n\nNew paragraph", deckCaptor.getValue().getDescription());
    }

    @Test
    public void shouldIgnoreLineBreaksWhenComparingDescriptions() {
        assertEquals(TournamentDeckScheduler.normalizeDescription(" This deck rocks.<br/>Second part"),
                TournamentDeckScheduler.normalizeDescription("This deck rocks.<br/><br/> Second part"));
        //Legacy db values with <br/> match archive values with plain line breaks
        assertEquals(TournamentDeckScheduler.normalizeDescription("A<br/>B"),
                TournamentDeckScheduler.normalizeDescription("A\r\nB"));
        assertEquals("A B", TournamentDeckScheduler.normalizeDescription("A<br>B"));
        assertEquals("A B", TournamentDeckScheduler.normalizeDescription("A\n\nB"));
        assertEquals(null, TournamentDeckScheduler.normalizeDescription(null));
    }

    @Test
    public void shouldAutoVerifyDeckUnmodifiedForAMonth() {
        DeckEntity actual = existingDeck(false);
        actual.setModificationDate(LocalDateTime.now().minusMonths(2));
        when(deckRepository.findById("tournament-2023event")).thenReturn(Optional.of(actual));
        when(deckCardRepository.findByIdDeckId("tournament-2023event")).thenReturn(List.of(
                deckCard(CRYPT_ID, 12, LocalDateTime.now().minusMonths(2)),
                deckCard(LIBRARY_ID, 60, LocalDateTime.now().minusMonths(2))));

        scheduler.parseDeck(twdaDeck());

        ArgumentCaptor<DeckEntity> deckCaptor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(deckCaptor.capture());
        assertEquals(true, deckCaptor.getValue().getVerified());
        verify(deckCardRepository, never()).saveAndFlush(any());
    }

    @Test
    public void shouldNotAutoVerifyRecentlyModifiedDeck() {
        DeckEntity actual = existingDeck(false);
        actual.setModificationDate(LocalDateTime.now().minusDays(5));
        when(deckRepository.findById("tournament-2023event")).thenReturn(Optional.of(actual));
        when(deckCardRepository.findByIdDeckId("tournament-2023event")).thenReturn(List.of(
                deckCard(CRYPT_ID, 12, LocalDateTime.now().minusMonths(2)),
                deckCard(LIBRARY_ID, 60, LocalDateTime.now().minusMonths(2))));

        scheduler.parseDeck(twdaDeck());

        verify(deckRepository, never()).saveAndFlush(any());
    }

    @Test
    public void shouldNotAutoVerifyDeckWithRecentlyModifiedCards() {
        DeckEntity actual = existingDeck(false);
        actual.setModificationDate(LocalDateTime.now().minusMonths(2));
        when(deckRepository.findById("tournament-2023event")).thenReturn(Optional.of(actual));
        when(deckCardRepository.findByIdDeckId("tournament-2023event")).thenReturn(List.of(
                deckCard(CRYPT_ID, 12, LocalDateTime.now().minusMonths(2)),
                deckCard(LIBRARY_ID, 60, LocalDateTime.now().minusDays(5))));

        scheduler.parseDeck(twdaDeck());

        verify(deckRepository, never()).saveAndFlush(any());
    }

    @Test
    public void shouldNotAutoVerifyDeckChangedInThisScan() {
        DeckEntity actual = existingDeck(false);
        //Metadata differs from the archive, so the deck is updated instead of promoted
        actual.setPlayers(10);
        actual.setModificationDate(LocalDateTime.now().minusMonths(2));
        when(deckRepository.findById("tournament-2023event")).thenReturn(Optional.of(actual));
        when(deckCardRepository.findByIdDeckId("tournament-2023event")).thenReturn(List.of(
                deckCard(CRYPT_ID, 12, LocalDateTime.now().minusMonths(2)),
                deckCard(LIBRARY_ID, 60, LocalDateTime.now().minusMonths(2))));

        scheduler.parseDeck(twdaDeck());

        ArgumentCaptor<DeckEntity> deckCaptor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(deckCaptor.capture());
        assertEquals(false, deckCaptor.getValue().getVerified());
    }

    @Test
    public void shouldParseRealTwdaSample() throws Exception {
        //Same mapper configuration as the scheduler, against real archive entries
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .findAndRegisterModules();
        Map<String, TwdaDeck> decks = mapper.readValue(getClass().getResourceAsStream("/twda-sample.json"),
                new TypeReference<Map<String, TwdaDeck>>() {
                });
        assertEquals(2, decks.size());
        TwdaDeck modern = decks.get("10043");
        assertEquals("10043", modern.getId());
        assertEquals("Shroud Mastery", modern.getEvent().getName());
        assertEquals("https://www.vekn.net/event-calendar/event/10043", modern.getEvent().getUrl());
        assertEquals(LocalDate.of(2022, 2, 6), modern.getEvent().getDate());
        assertEquals(12, modern.getEvent().getPlayersCount());
        assertEquals("Alex Romano", modern.getPlayer());
        assertEquals("Daylily", modern.getName());
        assertEquals(12, modern.getCards().stream()
                .filter(card -> "Crypt".equals(card.getKind())).mapToInt(TwdaCard::getCount).sum());
        assertEquals(90, modern.getCards().stream()
                .filter(card -> "Library".equals(card.getKind())).mapToInt(TwdaCard::getCount).sum());

        scheduler.parseDeck(modern);
        ArgumentCaptor<DeckEntity> deckCaptor = ArgumentCaptor.forClass(DeckEntity.class);
        verify(deckRepository).saveAndFlush(deckCaptor.capture());
        assertEquals("tournament-10043", deckCaptor.getValue().getId());
        assertEquals(2022, deckCaptor.getValue().getYear());
    }

    private TwdaDeck twdaDeck() {
        TwdaDeck deck = new TwdaDeck();
        deck.setId("2023event");
        TwdaEvent event = new TwdaEvent();
        event.setName("My Event 2023");
        event.setPlace("Somewhere, Spain");
        event.setDate(DATE);
        event.setPlayersCount(20);
        event.setUrl("https://example.org/event");
        deck.setEvent(event);
        deck.setPlayer("John Doe");
        deck.setName("My Deck");
        deck.setComment("Description: First line\nSecond line");
        deck.getCards().add(twdaCard(CRYPT_ID, 12));
        deck.getCards().add(twdaCard(LIBRARY_ID, 60));
        return deck;
    }

    private DeckCardEntity deckCard(int cardId, int number) {
        DeckCardEntity card = new DeckCardEntity();
        card.setId(new DeckCardEntity.DeckCardId());
        card.getId().setDeckId("tournament-2023event");
        card.getId().setCardId(cardId);
        card.setNumber(number);
        return card;
    }

    private DeckCardEntity deckCard(int cardId, int number, LocalDateTime modificationDate) {
        DeckCardEntity card = deckCard(cardId, number);
        card.setModificationDate(modificationDate);
        return card;
    }

    private TwdaCard card(TwdaDeck deck, int id) {
        return deck.getCards().stream().filter(card -> card.getId() == id).findFirst().orElseThrow();
    }

    private TwdaCard twdaCard(int id, int count) {
        TwdaCard card = new TwdaCard();
        card.setId(id);
        card.setCount(count);
        card.setPrintedName("Card " + id);
        card.setKind(id >= 200000 ? "Crypt" : "Library");
        return card;
    }

    /**
     * Existing row exactly matching {@link #twdaDeck()} but with a time-of-day enriched creation
     * date, as left behind by the legacy scraper.
     */
    private DeckEntity existingDeck(boolean verified) {
        return DeckEntity.builder()
                .id("tournament-2023event")
                .type(DeckType.TOURNAMENT)
                .source("http://www.vekn.fr/decks/twd.htm#2023event")
                .tournament("My Event 2023")
                .players(20)
                .year(2023)
                .author("John Doe")
                .url("https://example.org/event")
                .name("My Deck")
                .description("First line\nSecond line")
                .views(5L)
                .verified(verified)
                .creationDate(DATE.atTime(18, 30))
                .build();
    }
}
