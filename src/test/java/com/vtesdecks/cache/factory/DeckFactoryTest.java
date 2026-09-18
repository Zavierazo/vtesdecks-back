package com.vtesdecks.cache.factory;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.DeckCard;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.card.Card;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.CardErrataRepository;
import com.vtesdecks.jpa.repositories.CommentRepository;
import com.vtesdecks.jpa.repositories.DeckUserRepository;
import com.vtesdecks.jpa.repositories.DeckViewRepository;
import com.vtesdecks.jpa.repositories.ReactionRepository;
import com.vtesdecks.model.limitedformat.LimitedFormatPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class DeckFactoryTest {
    private DeckFactory factory;
    private CryptCache cryptCache;
    private LibraryCache libraryCache;
    private DeckEntity entity;

    @BeforeEach
    void setUp() {
        factory = new DeckFactory();
        cryptCache = mock(CryptCache.class);
        libraryCache = mock(LibraryCache.class);
        ReflectionTestUtils.setField(factory, "cryptCache", cryptCache);
        ReflectionTestUtils.setField(factory, "libraryCache", libraryCache);
        ReflectionTestUtils.setField(factory, "deckUserRepository", mock(DeckUserRepository.class));
        ReflectionTestUtils.setField(factory, "deckViewRepository", mock(DeckViewRepository.class));
        ReflectionTestUtils.setField(factory, "commentRepository", mock(CommentRepository.class));
        ReflectionTestUtils.setField(factory, "reactionRepository", mock(ReactionRepository.class));
        ReflectionTestUtils.setField(factory, "cardErrataRepository", mock(CardErrataRepository.class));
        entity = new DeckEntity();
        entity.setId("spoiler-deck");
        entity.setViews(0L);
        entity.setCreationDate(LocalDateTime.of(2026, 9, 18, 0, 0));
    }

    @Test
    void skipsMissingCardsAndKeepsAvailableCardsAndStats(CapturedOutput output) {
        when(cryptCache.get(200001)).thenReturn(crypt());
        when(libraryCache.get(100001)).thenReturn(library());

        Deck deck = factory.getDeck(entity, List.of(
                card(200002, 8), card(200001, 4), card(100002, 20), card(100001, 6)), List.of(), null);

        assertEquals(List.of(200001), deck.getCrypt().stream().map(Card::getId).toList());
        assertEquals(List.of(100001), deck.getLibrary().stream().map(Card::getId).toList());
        assertEquals(4, deck.getStats().getCrypt());
        assertEquals(6, deck.getStats().getLibrary());
        assertEquals(6, deck.getStats().getMaster());
        assertTrue(output.getOut().contains("Skipping missing crypt card 200002 when indexing deck spoiler-deck"));
        assertTrue(output.getOut().contains("Skipping missing library card 100002 when indexing deck spoiler-deck"));
    }

    @Test
    void buildsEmptyDeckWhenAllCardsAreMissingAndIncludesThemOnLaterRebuild() {
        List<DeckCard> cards = List.of(card(200001, 4), card(100001, 6));

        Deck initial = factory.getDeck(entity, cards, List.of(), null);

        assertTrue(initial.getCrypt().isEmpty());
        assertTrue(initial.getLibrary().isEmpty());
        assertEquals(0, initial.getStats().getCrypt());
        assertEquals(0, initial.getStats().getLibrary());
        when(cryptCache.get(200001)).thenReturn(crypt());
        when(libraryCache.get(100001)).thenReturn(library());

        Deck rebuilt = factory.getDeck(entity, cards, List.of(), null);

        assertEquals(1, rebuilt.getCrypt().size());
        assertEquals(1, rebuilt.getLibrary().size());
        assertEquals(4, rebuilt.getStats().getCrypt());
        assertEquals(6, rebuilt.getStats().getLibrary());
    }

    @Test
    void toleratesCardsDisappearingAfterInitialLookup(CapturedOutput output) {
        when(cryptCache.get(200001)).thenReturn(crypt(), (Crypt) null);
        when(cryptCache.get(200002)).thenReturn(crypt(), (Crypt) null);
        when(libraryCache.get(100001)).thenReturn(library(), (Library) null);

        Deck deck = factory.getDeck(entity, List.of(
                card(200001, 4), card(200002, 4), card(100001, 6)), List.of(), null);

        assertTrue(deck.getLibrary().isEmpty());
        assertTrue(deck.getGroups().isEmpty());
        assertTrue(deck.getClans().isEmpty());
        assertTrue(deck.getClanIcons().isEmpty());
        assertTrue(deck.getDisciplines().isEmpty());
        assertNull(deck.getPath());
        assertNull(deck.getPathIcon());
        assertEquals(0, deck.getStats().getCrypt());
        assertEquals(0, deck.getStats().getLibrary());
        assertTrue(deck.getTags().isEmpty());
        assertFalse(output.getOut().contains("Skipping missing"));
    }

    @Test
    void limitedFormatCheckSkipsCardsNoLongerInCache() {
        when(cryptCache.get(200001)).thenReturn(crypt());
        when(libraryCache.get(100001)).thenReturn(library());
        Deck deck = factory.getDeck(entity, List.of(card(200001, 4), card(100001, 6)), List.of(), null);
        when(cryptCache.get(200001)).thenReturn(null);
        when(libraryCache.get(100001)).thenReturn(null);
        LimitedFormatPayload format = new LimitedFormatPayload();
        format.setMinCrypt(0);
        format.setMinLibrary(0);

        Boolean valid = ReflectionTestUtils.invokeMethod(factory, "isValidForLimitedFormat", deck, format);

        assertEquals(Boolean.TRUE, valid);
    }

    private DeckCard card(int id, int number) {
        return new DeckCard(entity.getId(), id, number);
    }

    private Crypt crypt() {
        Crypt crypt = new Crypt();
        crypt.setId(200001);
        crypt.setTaints(Set.of());
        crypt.setCapacity(5);
        crypt.setGroup(6);
        crypt.setClan("Ventrue");
        crypt.setDisciplines(List.of());
        crypt.setSuperiorDisciplines(List.of());
        return crypt;
    }

    private Library library() {
        Library library = new Library();
        library.setId(100001);
        library.setTaints(Set.of());
        library.setType("Master");
        return library;
    }
}
