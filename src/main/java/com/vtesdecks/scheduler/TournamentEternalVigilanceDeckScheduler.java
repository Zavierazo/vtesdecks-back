package com.vtesdecks.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableMap;
import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckCardEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.model.eternalvigilance.EternalVigilanceCard;
import com.vtesdecks.model.eternalvigilance.EternalVigilanceDeck;
import com.vtesdecks.model.eternalvigilance.EternalVigilanceLibrarySection;
import com.vtesdecks.util.VtesUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Imports tournament winning decks published in the
 * <a href="https://github.com/gurchon-hall/eternal-vigilance">eternal-vigilance</a> repository.
 * <p>
 * Decks are only imported when they do not already come from the legacy
 * {@code vekn.fr/decks/twd.htm} scrap ({@link TournamentDeckScheduler}) and are not manually
 * verified, so curated/verified data is never overwritten.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentEternalVigilanceDeckScheduler {

    private static final String LEGACY_SOURCE_PREFIX = "http://www.vekn.fr/decks/twd.htm#";
    /**
     * Matches deck files stored as {@code YYYY/MM/<event_id>.yaml}.
     */
    private static final Pattern DECK_PATH = Pattern.compile("^\\d{4}/\\d{2}/(?<eventId>\\d+)\\.yaml$");
    private static final Pattern ADVANCED = Pattern.compile("\\s*\\(ADV\\)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_RETRIES = 4;
    private static final Map<String, String> TYPO_FIXES = ImmutableMap.<String, String>builder()
            .put("Pentex™ Subversion", "Pentex(TM) Subversion")
            .build();
    private static final String CREDIT = "Imported via <a href=\"https://github.com/gurchon-hall/channel-ten/tree/main\" target=\"_blank\" rel=\"noopener\">channel-ten</a> by Lyon Martin.";

    private final DeckRepository deckRepository;
    private final DeckCardRepository deckCardRepository;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;
    private final PlatformTransactionManager transactionManager;

    @Value("${jobs.eternalVigilance.repo:gurchon-hall/eternal-vigilance}")
    private String repo;
    @Value("${jobs.eternalVigilance.branch:main}")
    private String branch;

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .findAndRegisterModules();
    private TransactionTemplate transactionTemplate;

    @PostConstruct
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    //Import eternal-vigilance tournament decks once a day at 07:00
    @Scheduled(cron = "${jobs.scrappingEternalVigilanceDecksCron:0 0 7 * * *}")
    public void scrappingDecks() {
        log.info("Starting eternal-vigilance tournament decks import...");
        try {
            for (String path : listDeckPaths()) {
                String eventId = DECK_PATH.matcher(path).results().findFirst().map(r -> r.group("eventId")).orElse(null);
                if (eventId == null) {
                    continue;
                }
                try {
                    parseDeck(eventId, path);
                } catch (Exception e) {
                    log.error("Unable to import eternal-vigilance deck {}", path, e);
                }
            }
        } catch (Exception e) {
            log.error("Unable to import eternal-vigilance decks", e);
        }
        log.info("Finished eternal-vigilance tournament decks import");
    }

    /**
     * Lists every deck file path of the repository using a single recursive git tree request.
     */
    private List<String> listDeckPaths() throws Exception {
        String url = "https://api.github.com/repos/" + repo + "/git/trees/" + branch + "?recursive=1";
        JsonNode tree = jsonMapper.readTree(fetch(url)).get("tree");
        List<String> paths = new ArrayList<>();
        if (tree != null) {
            for (JsonNode node : tree) {
                String path = node.path("path").asText();
                if (DECK_PATH.matcher(path).matches()) {
                    paths.add(path);
                }
            }
        }
        log.info("Found {} eternal-vigilance deck files", paths.size());
        return paths;
    }

    private void parseDeck(String eventId, String path) throws Exception {
        String id = "tournament-" + eventId;
        Optional<DeckEntity> optionalDeck = deckRepository.findById(id);
        DeckEntity actual = optionalDeck.orElse(null);
        //Never override decks coming from the legacy twd.htm scrap nor manually verified decks
        if (actual != null) {
            if (actual.getSource() != null && actual.getSource().startsWith(LEGACY_SOURCE_PREFIX)) {
                log.debug("Skipping deck {}, already scrapped from {}", id, LEGACY_SOURCE_PREFIX);
                return;
            }
            if (Boolean.TRUE.equals(actual.getVerified())) {
                log.debug("Skipping deck {}, manually verified", id);
                return;
            }
        }

        String rawUrl = "https://raw.githubusercontent.com/" + repo + "/" + branch + "/" + path;
        EternalVigilanceDeck source = yamlMapper.readValue(fetch(rawUrl), EternalVigilanceDeck.class);
        if (source.getDeck() == null) {
            log.warn("Deck {} has no deck section, skipping", id);
            return;
        }

        DeckEntity deck = actual != null ? actual.toBuilder().build() : DeckEntity.builder().build();
        deck.setId(id);
        deck.setType(DeckType.TOURNAMENT);
        deck.setSource(source.getForumPostUrl());
        deck.setUrl(source.getEventUrl());
        deck.setTournament(source.getName());
        deck.setPlayers(source.getPlayersCount());
        deck.setAuthor(source.getWinner());
        deck.setViews(actual != null ? actual.getViews() : 0);
        deck.setVerified(actual != null ? actual.getVerified() : false);
        deck.setName(StringUtils.defaultIfBlank(source.getDeck().getName(), source.getName()));
        String description = StringUtils.trimToNull(source.getDeck().getDescription());
        deck.setDescription(description != null ? description + "<br/><br/>" + CREDIT : CREDIT);
        if (source.getDateStart() != null) {
            deck.setYear(source.getDateStart().getYear());
            deck.setCreationDate(source.getDateStart().atStartOfDay());
        }
        if (deck.getCreationDate() == null) {
            log.warn("Deck {} has no date, skipping", id);
            return;
        }

        Map<Integer, DeckCardEntity> deckCards = new HashMap<>();
        //Decks normally carry the card ids; older ones do not and need name matching. Log only once per deck.
        boolean missingIds = source.getDeck().getCrypt().stream().anyMatch(card -> card.getId() == null)
                || source.getDeck().getLibrarySections().stream()
                .flatMap(section -> section.getCards().stream()).anyMatch(card -> card.getId() == null);
        if (missingIds) {
            log.warn("Deck {} has cards without id, falling back to name matching", id);
        }
        for (EternalVigilanceCard card : source.getDeck().getCrypt()) {
            Integer cardId = card.getId() != null ? card.getId() : resolveCrypt(card);
            storeDeckCard(deck, deckCards, cardId, card.getCount());
        }
        for (EternalVigilanceLibrarySection section : source.getDeck().getLibrarySections()) {
            for (EternalVigilanceCard card : section.getCards()) {
                Integer cardId = card.getId() != null ? card.getId() : resolveLibrary(card.getName());
                storeDeckCard(deck, deckCards, cardId, card.getCount());
            }
        }

        if (!isValidDeck(deck, deckCards)) {
            return;
        }
        //Each deck is persisted in its own transaction so a failure only rolls back that deck
        transactionTemplate.executeWithoutResult(status -> persist(actual, deck, deckCards));
    }

    /**
     * Resolves a crypt card against the {@link CryptCache} by its exact name. When several cards
     * share the name (e.g. reprints in different groupings or advanced versions) they are
     * discriminated by the advanced flag and grouping; an ambiguous match fails the import.
     */
    private Integer resolveCrypt(EternalVigilanceCard card) {
        String fixedName = TYPO_FIXES.getOrDefault(card.getName(), card.getName());
        boolean advanced = ADVANCED.matcher(fixedName).find();
        String name = moveLeadingThe(ADVANCED.matcher(fixedName).replaceAll(""));
        //Grouping is "ANY" for cards usable with any group; only a numeric grouping disambiguates reprints.
        Integer grouping = StringUtils.isNumeric(card.getGrouping()) ? Integer.valueOf(card.getGrouping()) : null;
        List<Crypt> matches = new ArrayList<>();
        //Same-named reprints are stored with a grouping suffix (e.g. "Kalinda (G6)") to disambiguate them,
        //so the bare name only matches the original printing. Prefer the suffixed variant for the requested
        //grouping and only fall back to the bare name when it does not exist.
        if (grouping != null) {
            collectByExactName(name + " (G" + grouping + ")", matches);
        }
        if (matches.isEmpty()) {
            collectByExactName(name, matches);
        }
        if (matches.isEmpty()) {
            //Fall back to localized (i18n) names for decks published in a language other than English
            collectByExactI18nName(name, matches);
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Crypt card not found: '" + card.getName() + "'");
        }
        if (matches.size() > 1) {
            matches = matches.stream().filter(crypt -> crypt.isAdv() == advanced).toList();
        }
        if (matches.size() > 1 && grouping != null) {
            matches = matches.stream().filter(crypt -> Objects.equals(crypt.getGroup(), grouping)).toList();
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Ambiguous crypt card '" + card.getName() + "' grouping " + card.getGrouping() + ": " + matches.size() + " matches");
        }
        return matches.getFirst().getId();
    }

    private void collectByExactName(String name, List<Crypt> matches) {
        try (ResultSet<Crypt> result = cryptCache.selectByExactName(name)) {
            result.forEach(matches::add);
        }
    }

    private void collectByExactI18nName(String name, List<Crypt> matches) {
        try (ResultSet<Crypt> result = cryptCache.selectByExactI18nName(name)) {
            result.forEach(matches::add);
        }
    }

    private Integer resolveLibrary(String rawName) {
        String name = moveLeadingThe(TYPO_FIXES.getOrDefault(rawName, rawName));
        List<Library> matches = new ArrayList<>();
        try (ResultSet<Library> result = libraryCache.selectByExactName(name)) {
            result.forEach(matches::add);
        }
        if (matches.isEmpty()) {
            //Fall back to localized (i18n) names for decks published in a language other than English
            try (ResultSet<Library> result = libraryCache.selectByExactI18nName(name)) {
                result.forEach(matches::add);
            }
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Library card not found: '" + rawName + "'");
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Ambiguous library card '" + rawName + "': " + matches.size() + " matches");
        }
        return matches.getFirst().getId();
    }

    /**
     * Cards are stored with a trailing article (e.g. {@code Platinum Protocol, The}) whereas the
     * source may prefix it (e.g. {@code The Platinum Protocol}). Moves a leading {@code The} to the
     * end so both forms resolve to the same card.
     */
    private static String moveLeadingThe(String name) {
        String trimmed = StringUtils.trim(name);
        if (Strings.CI.startsWith(trimmed, "The ")) {
            return StringUtils.trim(trimmed.substring(4)) + ", The";
        }
        return trimmed;
    }

    private void storeDeckCard(DeckEntity deck, Map<Integer, DeckCardEntity> deckCards, Integer cardId, Integer number) {
        DeckCardEntity existing = deckCards.get(cardId);
        if (existing != null) {
            existing.setNumber(existing.getNumber() + number);
            return;
        }
        DeckCardEntity deckCard = new DeckCardEntity();
        deckCard.setId(new DeckCardEntity.DeckCardId());
        deckCard.getId().setDeckId(deck.getId());
        deckCard.getId().setCardId(cardId);
        deckCard.setNumber(number);
        deckCards.put(cardId, deckCard);
    }

    private void persist(DeckEntity actual, DeckEntity deck, Map<Integer, DeckCardEntity> deckCards) {
        boolean insert = actual == null;
        if (insert) {
            deckRepository.saveAndFlush(deck);
            log.info("Insert eternal-vigilance deck {}", deck.getId());
        } else if (!actual.equals(deck) || !Objects.equals(actual.getCreationDate(), deck.getCreationDate())) {
            deckRepository.saveAndFlush(deck);
            log.info("Update eternal-vigilance deck {}", deck.getId());
        }
        List<DeckCardEntity> dbCards = deckCardRepository.findByIdDeckId(deck.getId());
        for (DeckCardEntity card : deckCards.values()) {
            DeckCardEntity dbCard = dbCards.stream()
                    .filter(db -> db.getId().getCardId().equals(card.getId().getCardId()))
                    .findFirst().orElse(null);
            if (dbCard == null || !dbCard.equals(card)) {
                deckCardRepository.saveAndFlush(card);
            }
        }
        //Delete removed cards
        for (DeckCardEntity card : dbCards) {
            if (!deckCards.containsKey(card.getId().getCardId())) {
                log.warn("Removing card {} of deck {}", card.getId().getCardId(), deck.getId());
                deckCardRepository.deleteById(card.getId());
            }
        }
    }

    private boolean isValidDeck(DeckEntity deck, Map<Integer, DeckCardEntity> deckCards) {
        int crypt = 0;
        int library = 0;
        for (DeckCardEntity card : deckCards.values()) {
            if (VtesUtils.isCrypt(card.getId().getCardId())) {
                crypt += card.getNumber();
            } else if (VtesUtils.isLibrary(card.getId().getCardId())) {
                library += card.getNumber();
            }
        }
        if (crypt >= 12 && library >= 60 && library <= 90) {
            return true;
        }
        log.error("Invalid number of cards for deck {}. Crypt {} Library {}", deck.getId(), crypt, library);
        return false;
    }

    /**
     * Fetches the URL as text, retrying transient failures (5xx gateway/server errors and read
     * timeouts) with a linear backoff. Client errors (4xx, e.g. rate-limit 403 or 404) are not
     * retried.
     */
    private String fetch(String url) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return Jsoup.connect(url)
                        .ignoreContentType(true)
                        .maxBodySize(0)
                        .timeout(60000)
                        .userAgent("vtesdecks-bot")
                        .header("Accept", "application/vnd.github+json, text/plain, */*")
                        .execute()
                        .body();
            } catch (HttpStatusException e) {
                if (e.getStatusCode() < 500) {
                    throw e;
                }
                last = e;
            } catch (SocketTimeoutException e) {
                last = e;
            }
            if (attempt < MAX_RETRIES) {
                log.warn("Retry {}/{} fetching {}: {}", attempt, MAX_RETRIES - 1, url, last.toString());
                try {
                    Thread.sleep(attempt * 3000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while fetching " + url, ie);
                }
            }
        }
        throw last;
    }
}
