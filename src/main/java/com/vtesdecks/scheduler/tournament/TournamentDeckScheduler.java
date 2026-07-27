package com.vtesdecks.scheduler.tournament;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Card;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckCardEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.model.twda.TwdaCard;
import com.vtesdecks.model.twda.TwdaDeck;
import com.vtesdecks.model.twda.TwdaEvent;
import com.vtesdecks.util.VtesUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Imports tournament winning decks from the <a href="https://static.krcg.org/data/v5/twda.json">
 * KRCG TWDA v5</a> archive. Deck ids of legacy decks match the anchor ids of the official
 * {@code vekn.fr/decks/twd.htm} archive previously scrapped by {@link TournamentDeckOldScheduler},
 * and card entries carry the VEKN card ids used by the database, so no name matching is needed.
 * <p>
 * Manually verified decks are still scanned, but never modified: any difference against the
 * archive is only logged as a warning.
 * <p>
 * While scanning, decks whose deck and deck_card rows have not been modified for a month are
 * automatically promoted to verified.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentDeckScheduler {

    private static final String SOURCE_PREFIX = "http://www.vekn.fr/decks/twd.htm#";
    private static final int MAX_RETRIES = 4;
    //Half of the archive comments start with a redundant "Description:" label
    private static final Pattern DESCRIPTION_LABEL = Pattern.compile("^\\s*description\\s*:\\s*", Pattern.CASE_INSENSITIVE);

    private final DeckRepository deckRepository;
    private final DeckCardRepository deckCardRepository;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;
    private final PlatformTransactionManager transactionManager;

    @Value("${jobs.twda.url:https://static.krcg.org/data/v5/twda.json}")
    private String twdaUrl;

    private final ObjectMapper jsonMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .findAndRegisterModules();
    private TransactionTemplate transactionTemplate;

    @PostConstruct
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    //Update tournament decks once a day at 06:30
    @Scheduled(cron = "${jobs.scrappingDecksCron:0 30 6 * * *}")
    public void scrappingDecks() {
        log.info("Starting tournament decks import from TWDA...");
        try {
            Map<String, TwdaDeck> decks = jsonMapper.readValue(fetch(twdaUrl), new TypeReference<Map<String, TwdaDeck>>() {
            });
            log.info("Found {} TWDA decks", decks.size());
            for (TwdaDeck deck : decks.values()) {
                try {
                    parseDeck(deck);
                } catch (Exception e) {
                    log.error("Unable to import TWDA deck {}", deck.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("Unable to import TWDA decks", e);
        }
        log.info("Finished tournament decks import from TWDA");
    }

    void parseDeck(TwdaDeck source) {
        String id = "tournament-" + source.getId();
        TwdaEvent event = source.getEvent();
        if (event == null || event.getDate() == null) {
            log.warn("Deck {} has no event date, skipping", id);
            return;
        }
        DeckEntity actual = deckRepository.findById(id).orElse(null);

        DeckEntity deck = actual != null ? actual.toBuilder().build() : DeckEntity.builder().build();
        deck.setId(id);
        deck.setType(DeckType.TOURNAMENT);
        deck.setSource(SOURCE_PREFIX + source.getId());
        deck.setTournament(event.getName());
        //The archive reports 0 players when the attendance is unknown
        deck.setPlayers(event.getPlayersCount() != null && event.getPlayersCount() > 0 ? event.getPlayersCount() : null);
        deck.setYear(event.getDate().getYear());
        deck.setAuthor(source.getPlayer());
        deck.setUrl(StringUtils.isNotBlank(event.getUrl()) ? event.getUrl() : null);
        deck.setViews(actual != null ? actual.getViews() : 0);
        deck.setVerified(actual != null ? actual.getVerified() : false);
        String name = source.getName();
        if (StringUtils.isBlank(name) && actual != null && StringUtils.isNotBlank(actual.getName())) {
            //The archive has no name for this deck: keep the existing one instead of autogenerating it
            name = actual.getName();
        }
        deck.setName(StringUtils.defaultIfBlank(name, getFallbackName(source)));
        if (deck.getName() == null) {
            log.warn("Unable to define name for deck {}, skipping", id);
            return;
        }
        String comments = StringUtils.trimToNull(source.getComment());
        if (comments != null) {
            comments = StringUtils.trimToNull(DESCRIPTION_LABEL.matcher(comments).replaceFirst(""));
        }
        if (comments != null) {
            //Remove indentation so no line starts with spaces
            comments = comments.replaceAll("[ \\t]*(\\r?\\n)[ \\t]*", "$1");
        }
        //Line breaks are kept as-is, the frontend renders them correctly
        deck.setDescription(comments);
        //The legacy scraper enriched creation dates with the time of day of the event page, keep
        //them when the archive only differs on the time part
        if (actual != null && actual.getCreationDate() != null && actual.getCreationDate().toLocalDate().equals(event.getDate())) {
            deck.setCreationDate(actual.getCreationDate());
        } else {
            deck.setCreationDate(event.getDate().atStartOfDay());
        }

        Map<Integer, DeckCardEntity> deckCards = buildCards(deck.getId(), source);
        if (!isValidDeck(deck, deckCards)) {
            return;
        }
        if (actual == null && isDuplicateOfOtherDeck(deck)) {
            return;
        }
        if (actual != null && Boolean.TRUE.equals(actual.getVerified())) {
            //Verified decks are curated by hand: scan them but never modify anything
            reportVerifiedDifferences(actual, deck, deckCards);
            return;
        }
        //Each deck is persisted in its own transaction so a failure only rolls back that deck
        transactionTemplate.executeWithoutResult(status -> persist(actual, deck, deckCards));
    }

    private String getFallbackName(TwdaDeck source) {
        TwdaEvent event = source.getEvent();
        if (StringUtils.isBlank(event.getName())) {
            return null;
        }
        if (StringUtils.isNotBlank(source.getPlayer())) {
            return source.getPlayer() + ", " + event.getName() + ", " + event.getDate().getYear();
        }
        return event.getName() + ", " + event.getDate().getYear();
    }

    private Map<Integer, DeckCardEntity> buildCards(String deckId, TwdaDeck source) {
        Map<Integer, DeckCardEntity> deckCards = new HashMap<>();
        for (TwdaCard card : source.getCards()) {
            storeDeckCard(deckId, deckCards, card);
        }
        return deckCards;
    }

    private void storeDeckCard(String deckId, Map<Integer, DeckCardEntity> deckCards, TwdaCard card) {
        Integer cardId = card.getId();
        if (cardId == null || !existsCard(cardId)) {
            log.error("Unknown card {} ('{}') on deck {}", cardId, card.getPrintedName(), deckId);
            return;
        }
        DeckCardEntity existing = deckCards.get(cardId);
        if (existing != null) {
            existing.setNumber(existing.getNumber() + card.getCount());
            return;
        }
        DeckCardEntity deckCard = new DeckCardEntity();
        deckCard.setId(new DeckCardEntity.DeckCardId());
        deckCard.getId().setDeckId(deckId);
        deckCard.getId().setCardId(cardId);
        deckCard.setNumber(card.getCount());
        deckCards.put(cardId, deckCard);
    }

    private boolean existsCard(Integer cardId) {
        if (VtesUtils.isCrypt(cardId)) {
            return cryptCache.get(cardId) != null;
        } else if (VtesUtils.isLibrary(cardId)) {
            return libraryCache.get(cardId) != null;
        }
        return false;
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
        } else if (deck.getYear() < 2015) {
            //Some old decks have an illegal amount of cards, be lenient with them
            return true;
        } else {
            log.error("Invalid number of cards for deck {}. Crypt {} Library {}", deck.getId(), crypt, library);
            return false;
        }
    }

    /**
     * Guards against the same event being imported under another id by a different source (e.g.
     * {@link TournamentEternalVigilanceDeckScheduler} keys decks by VEKN event id).
     */
    private boolean isDuplicateOfOtherDeck(DeckEntity deck) {
        List<DeckEntity> existingDecks = deckRepository.findByTypeAndNameContainingIgnoreCase(DeckType.TOURNAMENT, deck.getName());
        for (DeckEntity existingDeck : existingDecks) {
            if (!existingDeck.getId().equals(deck.getId())
                    && existingDeck.getTournament() != null && existingDeck.getTournament().equalsIgnoreCase(deck.getTournament())
                    && existingDeck.getCreationDate().toLocalDate().equals(deck.getCreationDate().toLocalDate())
                    && Boolean.FALSE.equals(existingDeck.getDeleted())) {
                log.warn("Possible duplicate deck found: {} with id {}", deck.getName(), existingDeck.getId());
                return true;
            }
        }
        return false;
    }

    private void reportVerifiedDifferences(DeckEntity actual, DeckEntity deck, Map<Integer, DeckCardEntity> deckCards) {
        String id = deck.getId();
        diffField(id, "tournament", actual.getTournament(), deck.getTournament());
        diffField(id, "players", actual.getPlayers(), deck.getPlayers());
        diffField(id, "year", actual.getYear(), deck.getYear());
        diffField(id, "author", actual.getAuthor(), deck.getAuthor());
        diffField(id, "url", actual.getUrl(), deck.getUrl());
        diffField(id, "source", actual.getSource(), deck.getSource());
        diffField(id, "name", actual.getName(), deck.getName());
        diffField(id, "creationDate", actual.getCreationDate(), deck.getCreationDate());
        List<DeckCardEntity> dbCards = deckCardRepository.findByIdDeckId(id);
        List<String> cardDiffs = new ArrayList<>();
        for (DeckCardEntity card : deckCards.values()) {
            DeckCardEntity dbCard = dbCards.stream()
                    .filter(db -> db.getId().getCardId().equals(card.getId().getCardId()))
                    .findFirst().orElse(null);
            if (dbCard == null) {
                cardDiffs.add(cardLabel(card.getId().getCardId()) + " db=0 twda=" + card.getNumber());
            } else if (!Objects.equals(dbCard.getNumber(), card.getNumber())) {
                cardDiffs.add(cardLabel(card.getId().getCardId()) + " db=" + dbCard.getNumber() + " twda=" + card.getNumber());
            }
        }
        for (DeckCardEntity dbCard : dbCards) {
            if (!deckCards.containsKey(dbCard.getId().getCardId())) {
                cardDiffs.add(cardLabel(dbCard.getId().getCardId()) + " db=" + dbCard.getNumber() + " twda=0");
            }
        }
        if (!cardDiffs.isEmpty()) {
            log.warn("Verified deck {} differs on cards: {}", id, String.join(", ", cardDiffs));
        }
    }

    private String cardLabel(Integer cardId) {
        Card card = VtesUtils.isCrypt(cardId) ? cryptCache.get(cardId) : libraryCache.get(cardId);
        return (card != null && card.getName() != null ? card.getName() : "?") + " (" + cardId + ")";
    }

    /**
     * Line breaks and spacing changed between the legacy scraper and the archive import, ignore
     * them when checking verified decks so only real text changes are reported.
     */
    static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        return StringUtils.normalizeSpace(description.replaceAll("(?i)<br\\s*/?>", " "));
    }

    private void diffField(String deckId, String field, Object db, Object twda) {
        if (!Objects.equals(db, twda)) {
            log.warn("Verified deck {} differs on {}: db='{}' twda='{}'", deckId, field, db, twda);
        }
    }

    private void persist(DeckEntity actual, DeckEntity deck, Map<Integer, DeckCardEntity> deckCards) {
        boolean insert = actual == null;
        boolean changed = false;
        if (insert) {
            deckRepository.saveAndFlush(deck);
            log.debug("Insert deck {}", deck.getId());
            changed = true;
        } else if (!actual.equals(deck) || !Objects.equals(actual.getCreationDate(), deck.getCreationDate())) {
            log.warn("Deck {} updated metadata", deck.getId());
            deckRepository.saveAndFlush(deck);
            changed = true;
        }
        List<DeckCardEntity> dbCards = deckCardRepository.findByIdDeckId(deck.getId());
        for (DeckCardEntity card : deckCards.values()) {
            DeckCardEntity dbCard = dbCards.stream()
                    .filter(db -> db.getId().getCardId().equals(card.getId().getCardId()))
                    .findFirst().orElse(null);
            if (dbCard == null) {
                if (!insert) {
                    log.warn("New card detected for card {} of deck {}", card, deck.getId());
                }
                deckCardRepository.saveAndFlush(card);
                changed = true;
            } else if (!dbCard.equals(card)) {
                log.warn("Found new card count for card {} of deck {}", card, deck.getId());
                deckCardRepository.saveAndFlush(card);
                changed = true;
            }
        }
        //Delete removed cards
        for (DeckCardEntity card : dbCards) {
            if (!deckCards.containsKey(card.getId().getCardId())) {
                log.warn("Missing card {} of deck {}", card, deck.getId());
                deckCardRepository.deleteById(card.getId());
                changed = true;
            }
        }
        //A deck stable for a month is promoted to verified, locking it against future scans
        if (!insert && !changed && isUnmodifiedForAMonth(actual, dbCards)) {
            deck.setVerified(true);
            deckRepository.saveAndFlush(deck);
            log.info("Auto-verified deck {} unmodified for a month", deck.getId());
        }
    }

    private boolean isUnmodifiedForAMonth(DeckEntity actual, List<DeckCardEntity> dbCards) {
        LocalDateTime threshold = LocalDateTime.now().minusMonths(1);
        if (actual.getModificationDate() == null || !actual.getModificationDate().isBefore(threshold)) {
            return false;
        }
        return dbCards.stream()
                .allMatch(card -> card.getModificationDate() != null && card.getModificationDate().isBefore(threshold));
    }

    /**
     * Fetches the URL as text, retrying transient failures (5xx gateway/server errors and read
     * timeouts) with a linear backoff. Client errors (4xx) are not retried.
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
                        .header("Accept", "application/json, text/plain, */*")
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
