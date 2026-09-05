package com.vtesdecks.api.controller;

import com.googlecode.cqengine.IndexedCollection;
import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.api.service.AchievementService;
import com.vtesdecks.api.service.ApiCommentService;
import com.vtesdecks.api.service.ApiDeckService;
import com.vtesdecks.api.service.ApiUserService;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.enums.CardPrintingPreference;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.api.ApiDecks;
import com.vtesdecks.model.api.ApiResponse;
import com.vtesdecks.model.api.ApiUserSettings;
import com.vtesdecks.service.DeckUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class ApiUserControllerTest {
    private static final int USER_ID = 42;

    @Mock
    private DeckUserService deckUserService;
    @Mock
    private ApiDeckService deckService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApiCommentService apiCommentService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApiUserService userService;
    @Mock
    private AchievementService achievementService;
    @InjectMocks
    private ApiUserController controller;

    private UserEntity user;

    @BeforeEach
    public void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(USER_ID), null, List.of()));
        user = new UserEntity();
        user.setId(USER_ID);
        user.setUsername("testuser");
        user.setEmail("testuser@example.com");
        user.setDisplayName("Test User");
        user.setCardPrintingPreference(CardPrintingPreference.NEWEST);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refreshUserEvaluatesAchievements() {
        controller.refreshUser();

        verify(achievementService).activity(USER_ID);
    }

    @Test
    public void shouldPersistCardPrintingPreference() {
        ApiUserSettings settings = new ApiUserSettings();
        settings.setCardPrintingPreference(CardPrintingPreference.FIRST);

        ApiResponse response = controller.changeSettings(settings);

        assertTrue(response.getSuccessful());
        verify(userRepository).save(argThat(saved -> saved.getCardPrintingPreference() == CardPrintingPreference.FIRST));
    }

    @Test
    public void shouldKeepCardPrintingPreferenceWhenNotSent() {
        ApiUserSettings settings = new ApiUserSettings();
        settings.setDisplayName("New Name");

        ApiResponse response = controller.changeSettings(settings);

        assertTrue(response.getSuccessful());
        verify(userRepository).save(argThat(saved -> saved.getCardPrintingPreference() == CardPrintingPreference.NEWEST));
    }

    @Test
    public void shouldNotSaveWhenNothingChanged() {
        ApiUserSettings settings = new ApiUserSettings();

        ApiResponse response = controller.changeSettings(settings);

        assertNull(response.getSuccessful());
        verify(userRepository, never()).save(user);
        assertEquals(CardPrintingPreference.NEWEST, user.getCardPrintingPreference());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldFilterDecksByAllAnySingleAndTournamentName() {
        DeckIndex index = new DeckIndex();
        IndexedCollection<Deck> decks = (IndexedCollection<Deck>) ReflectionTestUtils.getField(index, "decks");
        decks.add(deck("brujah", Set.of("Brujah"), Set.of("Celerity", "Potence"), "Madrid Grand Prix"));
        decks.add(deck("gangrel", Set.of("Gangrel"), Set.of("Animalism", "Protean"), "Paris Open"));
        decks.add(deck("mixed", Set.of("Brujah", "Gangrel"), Set.of("Animalism", "Celerity"), null));

        assertEquals(List.of("mixed"), ids(index, DeckQuery.builder()
                .clans(List.of("Brujah", "Gangrel"))
                .disciplines(List.of("Animalism", "Celerity"))
                .build()));
        assertEquals(Set.of("brujah", "gangrel"), Set.copyOf(ids(index, DeckQuery.builder()
                .clans(List.of("Brujah", "Gangrel")).clanMode("or")
                .disciplines(List.of("Potence", "Protean")).disciplineMode("or")
                .build())));
        assertEquals(List.of(), ids(index, DeckQuery.builder()
                .clans(List.of("Brujah", "Gangrel")).clanMode("or").singleClan(true)
                .disciplines(List.of("Animalism", "Celerity")).disciplineMode("or").singleDiscipline(true)
                .build()));
        assertEquals(List.of("brujah"), ids(index, DeckQuery.builder().tournament("gRaNd pRiX").build()));
        assertEquals(List.of("gangrel"), ids(index, DeckQuery.builder()
                .notClans(List.of("Brujah"))
                .notDisciplines(List.of("Celerity"))
                .build()));
    }

    @Test
    public void shouldMapDeckMatchModesAndTournament() throws Exception {
        ApiDeckController deckController = new ApiDeckController();
        ReflectionTestUtils.setField(deckController, "deckService", deckService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(deckController).build();
        when(deckService.getDecks(any(DeckQuery.class), isNull(), isNull(), any(), anyInt(), anyInt()))
                .thenReturn(new ApiDecks());

        mockMvc.perform(get("/api/1.0/decks")
                        .param("clans", "Brujah,Gangrel")
                        .param("notClans", "Toreador")
                        .param("clanMode", "or")
                        .param("disciplines", "Celerity,Potence")
                        .param("notDisciplines", "Dominate")
                        .param("disciplineMode", "or")
                        .param("tournament", "Grand Prix"))
                .andExpect(status().isOk());

        ArgumentCaptor<DeckQuery> captor = ArgumentCaptor.forClass(DeckQuery.class);
        verify(deckService).getDecks(captor.capture(), isNull(), isNull(), any(), anyInt(), anyInt());
        DeckQuery query = captor.getValue();
        assertEquals(List.of("Brujah", "Gangrel"), query.getClans());
        assertEquals(List.of("Celerity", "Potence"), query.getDisciplines());
        assertEquals(List.of("Toreador"), query.getNotClans());
        assertEquals(List.of("Dominate"), query.getNotDisciplines());
        assertTrue(query.isClanAny());
        assertTrue(query.isDisciplineAny());
        assertEquals("Grand Prix", query.getTournament());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldFilterDecksByPlaceCountryAndRounds() {
        DeckIndex index = new DeckIndex();
        IndexedCollection<Deck> decks = (IndexedCollection<Deck>) ReflectionTestUtils.getField(index, "decks");
        decks.add(event("madrid", "Madrid, Spain", "Spain", 3));
        decks.add(event("newark", "Newark (OH), USA", "United States", 2));
        decks.add(event("online", "Online", null, null));

        //The place already contains the country name
        assertEquals(List.of("madrid"), ids(index, DeckQuery.builder().place("sPaIn").build()));
        //Only the country matches
        assertEquals(List.of("newark"), ids(index, DeckQuery.builder().place("united").build()));
        assertEquals(List.of("newark"), ids(index, DeckQuery.builder().place("newark").build()));
        assertEquals(List.of("online"), ids(index, DeckQuery.builder().place("onl").build()));
        assertEquals(List.of(), ids(index, DeckQuery.builder().place("france").build()));
        assertEquals(List.of("madrid"), ids(index, DeckQuery.builder().rounds(List.of(3)).build()));
        assertEquals(Set.of("madrid", "newark"), Set.copyOf(ids(index, DeckQuery.builder().rounds(List.of(2, 3)).build())));
    }

    @Test
    public void shouldMapPlaceAndRoundsParameters() throws Exception {
        ApiDeckController deckController = new ApiDeckController();
        ReflectionTestUtils.setField(deckController, "deckService", deckService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(deckController).build();
        when(deckService.getDecks(any(DeckQuery.class), isNull(), isNull(), any(), anyInt(), anyInt()))
                .thenReturn(new ApiDecks());

        mockMvc.perform(get("/api/1.0/decks")
                        .param("place", "Spain")
                        .param("rounds", "2,3"))
                .andExpect(status().isOk());

        ArgumentCaptor<DeckQuery> captor = ArgumentCaptor.forClass(DeckQuery.class);
        verify(deckService).getDecks(captor.capture(), isNull(), isNull(), any(), anyInt(), anyInt());
        DeckQuery query = captor.getValue();
        assertEquals("Spain", query.getPlace());
        assertEquals(List.of(2, 3), query.getRounds());
    }

    private Deck event(String id, String place, String country, Integer rounds) {
        Deck deck = deck(id, Set.of("Brujah"), Set.of("Celerity"), "Event " + id);
        deck.setPlace(place);
        deck.setCountry(country);
        deck.setRounds(rounds);
        return deck;
    }

    private List<String> ids(DeckIndex index, DeckQuery query) {
        try (ResultSet<Deck> result = index.selectAll(query)) {
            return result.stream().map(Deck::getId).toList();
        }
    }

    private Deck deck(String id, Set<String> clans, Set<String> disciplines, String tournament) {
        Deck deck = new Deck();
        deck.setId(id);
        deck.setName(id);
        deck.setPublished(true);
        deck.setType(DeckType.TOURNAMENT);
        deck.setClans(clans);
        deck.setDisciplines(disciplines);
        deck.setTournament(tournament);
        deck.setCreationDate(LocalDateTime.now());
        return deck;
    }
}
