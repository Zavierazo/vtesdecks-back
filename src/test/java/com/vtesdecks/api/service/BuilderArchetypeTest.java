package com.vtesdecks.api.service;

import com.vtesdecks.api.GlobalExceptionHandler;
import com.vtesdecks.api.controller.ApiDeckBuilderController;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.DeckCardIndex;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.jpa.repositories.DeckArchetypeRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.ArchetypeAttributeRequirement;
import com.vtesdecks.model.ArchetypeCardRequirement;
import com.vtesdecks.model.api.ApiCard;
import com.vtesdecks.model.api.ApiDeckBuilder;
import com.vtesdecks.service.DeckArchetypeMatcher;
import com.vtesdecks.service.DeckService;
import com.vtesdecks.util.CosineSimilarityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BuilderArchetypeTest {
    @Mock DeckArchetypeRepository archetypes;
    @Mock DeckService decks;
    @Mock DeckCardIndex index;
    @Mock CryptCache crypt;
    @Mock LibraryCache library;
    @Mock DeckRepository deckRepository;
    @Mock DeckCardRepository cardRepository;
    @Mock MessageProducer messages;
    @InjectMocks ApiDeckBuilderService service;
    private MockMvc mvc;

    @BeforeEach void setup() {
        var matcher = new DeckArchetypeMatcher(archetypes, decks, index, crypt, library);
        ReflectionTestUtils.setField(service, "archetypeMatcher", matcher);
        var controller = new ApiDeckBuilderController();
        ReflectionTestUtils.setField(controller, "deckBuilderService", service);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private ApiCard card(Integer id, Integer quantity) {
        var card = new ApiCard();
        card.setId(id);
        card.setNumber(quantity);
        return card;
    }

    private ApiDeckBuilder builder(ApiCard... cards) {
        var builder = new ApiDeckBuilder();
        builder.setId("someone-elses-saved-deck");
        builder.setCards(Arrays.asList(cards));
        return builder;
    }

    private DeckArchetypeEntity reference(int id, Map<Integer, Integer> cards) {
        String deckId = "reference-" + id;
        var deck = mock(DeckSummary.class);
        when(deck.getId()).thenReturn(deckId);
        lenient().when(deck.getL2Norm()).thenReturn(CosineSimilarityUtils.computeL2Norm(cards));
        when(decks.getSummary(deckId)).thenReturn(deck);
        when(index.getCardCounts(deckId)).thenReturn(cards);
        return DeckArchetypeEntity.builder().id(id).name("Archetype " + id)
                .icon("gangrel").type("Wall").deckId(deckId).enabled(true).build();
    }

    @Test void postClassifiesSubmittedCardsWithoutLoadingOrSavingTheDeck() throws Exception {
        when(library.get(100001)).thenReturn(mock(Library.class));
        var weaker = reference(1, Map.of(100001, 10, 100002, 5));
        var strongest = reference(2, Map.of(100001, 10));
        when(archetypes.findAll()).thenReturn(List.of(weaker, strongest));

        mvc.perform(post("/api/1.0/user/decks/builder/archetype").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"someone-elses-saved-deck","name":"Unsaved draft",
                                 "extra":{"advent":{"year":2026,"day":3}},
                                 "cards":[{"id":100001,"number":10},{"id":100002,"number":0}]}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Archetype 2"))
                .andExpect(jsonPath("$.icon").value("gangrel"))
                .andExpect(jsonPath("$.type").value("Wall"))
                .andExpect(jsonPath("$.deckId").doesNotExist());
        verify(decks, never()).getSummary("someone-elses-saved-deck");
        verify(library, never()).get(100002);
        verifyNoInteractions(deckRepository, cardRepository, messages);
    }

    @Test void noQualifyingMatchReturnsNoContent() throws Exception {
        when(library.get(100001)).thenReturn(mock(Library.class));
        var reference = reference(1, Map.of(100002, 10));
        when(archetypes.findAll()).thenReturn(List.of(reference));
        mvc.perform(post("/api/1.0/user/decks/builder/archetype").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cards\":[{\"id\":100001,\"number\":10}]}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    @Test void emptyAndConsideringOnlyDraftsDoNotLoadReferences() throws Exception {
        for (String body : List.of("{}", "{\"cards\":[]}", "{\"cards\":[{\"id\":999999,\"number\":0}]}")) {
            mvc.perform(post("/api/1.0/user/decks/builder/archetype").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isNoContent());
        }
        verifyNoInteractions(archetypes, decks, index, crypt, library, deckRepository, cardRepository, messages);
    }

    @Test void cardRequirementsCannotBeSatisfiedByConsideringCards() {
        when(library.get(100001)).thenReturn(mock(Library.class));
        var reference = reference(1, Map.of(100001, 10));
        reference.setCardRequirements(List.of(new ArchetypeCardRequirement(100002, 1)));
        when(archetypes.findAll()).thenReturn(List.of(reference));
        assertNull(service.getArchetype(builder(card(100001, 10), card(100002, 0))));
    }

    @Test void attributeRulesUseTheSubmittedQuantitiesAndCatalog() {
        var vampire = mock(Crypt.class);
        when(vampire.getClan()).thenReturn("Gangrel");
        when(crypt.get(200001)).thenReturn(vampire);
        var action = mock(Library.class);
        when(action.getTypes()).thenReturn(Set.of("Political Action"));
        when(library.get(100001)).thenReturn(action);
        var reference = reference(1, Map.of(200001, 4, 100001, 8));
        reference.setAttributeRequirements(List.of(
                new ArchetypeAttributeRequirement(ArchetypeAttributeRequirement.Type.CRYPT_CLAN, "Gangrel", 4),
                new ArchetypeAttributeRequirement(ArchetypeAttributeRequirement.Type.LIBRARY_TYPE, "Political Action", 8)));
        when(archetypes.findAll()).thenReturn(List.of(reference));
        assertNull(service.getArchetype(builder(card(200001, 3), card(100001, 8))));
        assertNull(service.getArchetype(builder(card(200001, 4), card(100001, 7))));
        assertEquals(1, service.getArchetype(builder(card(200001, 4), card(100001, 8))).getId());
    }

    @Test void malformedEntriesAreRejectedBeforeClassification() {
        for (ApiDeckBuilder input : List.of(builder((ApiCard) null), builder(card(null, 1)),
                builder(card(100001, null)), builder(card(100001, -1)),
                builder(card(100001, 0), card(100001, 0)))) {
            var error = assertThrows(ResponseStatusException.class, () -> service.getArchetype(input));
            assertEquals(400, error.getStatusCode().value());
        }
        verifyNoInteractions(archetypes, decks, index, crypt, library, deckRepository, cardRepository, messages);
    }

    @Test void unknownActiveCardIsRejectedRatherThanIgnored() throws Exception {
        mvc.perform(post("/api/1.0/user/decks/builder/archetype").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cards\":[{\"id\":999999,\"number\":1}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(archetypes, decks, index, deckRepository, cardRepository, messages);
    }

    @Test void absentBodyIsRejected() throws Exception {
        mvc.perform(post("/api/1.0/user/decks/builder/archetype").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(archetypes, decks, index, deckRepository, cardRepository, messages);
    }

    @Test void largeQuantitiesDoNotOverflowTheSimilarityNorm() {
        when(library.get(100001)).thenReturn(mock(Library.class));
        var reference = reference(1, Map.of(100001, 10));
        when(archetypes.findAll()).thenReturn(List.of(reference));
        assertEquals(1, service.getArchetype(builder(card(100001, Integer.MAX_VALUE))).getId());
    }
}
