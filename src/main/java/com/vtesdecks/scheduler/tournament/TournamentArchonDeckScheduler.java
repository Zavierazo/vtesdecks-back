package com.vtesdecks.scheduler.tournament;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.integration.ArchonClient;
import com.vtesdecks.jpa.entity.DeckCardEntity;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.entity.TournamentSchedulerOwner;
import com.vtesdecks.jpa.repositories.ArchonUserRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.model.archon.ArchonDeck;
import com.vtesdecks.model.archon.ArchonTournament;
import com.vtesdecks.model.archon.ArchonUser;
import com.vtesdecks.scheduler.tournament.helpers.TournamentDeckName;
import com.vtesdecks.scheduler.tournament.helpers.TournamentImportPolicy;
import com.vtesdecks.util.VtesUtils;
import feign.Response;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class TournamentArchonDeckScheduler {
    private static final String ARCHON_TOURNAMENT_URL = "https://archon.vekn.net/tournaments/";
    private static final String VEKN_EVENT_URL = "https://www.vekn.net/event-calendar/event/";
    private static final Set<String> PUBLISHED_DECKLIST_MODES = Set.of("Winner", "Finalists", "All");

    private final DeckRepository deckRepository;
    private final DeckCardRepository deckCardRepository;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;
    private final PlatformTransactionManager transactionManager;
    private final ArchonClient archonClient;
    private final ArchonUserRepository archonUserRepository;
    private final ObjectMapper jsonMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .findAndRegisterModules();

    @Value("${archon.clientId:}")
    private String clientId;
    @Value("${archon.clientSecret:}")
    private String clientSecret;

    private TournamentImportPolicy importPolicy;

    @PostConstruct
    void setUp() {
        importPolicy = new TournamentImportPolicy(deckRepository, new TransactionTemplate(transactionManager));
    }

    // Import Archon finalist decks once a day after the TWDA and Eternal Vigilance jobs.
    @Scheduled(cron = "${jobs.scrappingArchonDecksCron:0 30 7 * * *}")
    public void scrappingDecks() {
        if (StringUtils.isBlank(clientId) || StringUtils.isBlank(clientSecret)) {
            log.warn("Skipping Archon tournament deck import because ARCHON_CLIENT_ID or ARCHON_CLIENT_SECRET is not configured");
            return;
        }
        log.info("Starting Archon finalist deck import...");
        Path export = null;
        try {
            String authorization = "Bearer " + fetchToken();
            export = downloadExport(authorization);
            List<ArchonTournament> tournaments = readTournaments(export);
            List<ArchonTournament> eligible = new ArrayList<>();
            Set<String> finalistUids = new HashSet<>();
            for (ArchonTournament tournament : tournaments) {
                if (!isFinishedFinal(tournament)) {
                    continue;
                }
                String eventId = getEventId(tournament);
                if (eventId == null) {
                    log.warn("Skipping Archon tournament {} because it has no event code", tournament.getUid());
                    continue;
                }
                List<Finalist> finalists = finalists(tournament);
                if (finalists.isEmpty()) {
                    log.warn("Skipping Archon tournament {} because its winner cannot be identified", tournament.getUid());
                    continue;
                }
                eligible.add(tournament);
                finalists.stream().map(Finalist::userUid).forEach(finalistUids::add);
            }
            Map<String, String> veknIds = loadVeknIds(export, finalistUids);
            Map<String, List<ArchonDeck>> decksByTournament = loadTournamentDecks(export, eligible);
            for (ArchonTournament tournament : eligible) {
                try {
                    importTournament(tournament, decksByTournament.getOrDefault(tournament.getUid(), List.of()), veknIds);
                } catch (Exception e) {
                    log.error("Unable to import Archon tournament {}", tournament.getUid(), e);
                }
            }
        } catch (Exception e) {
            log.error("Unable to import Archon finalist decks", e);
        } finally {
            deleteExport(export);
        }
        log.info("Finished Archon finalist deck import");
    }

    boolean isFinishedFinal(ArchonTournament tournament) {
        return tournament != null
                && "Finished".equals(tournament.getState())
                && PUBLISHED_DECKLIST_MODES.contains(tournament.getDecklistsMode())
                && StringUtils.isNotBlank(tournament.getWinner())
                && tournament.getStart() != null
                && !tournament.getStart().isBefore(LocalDateTime.now().minusYears(1));
    }

    private void importTournament(ArchonTournament tournament, List<ArchonDeck> tournamentDecks, Map<String, String> veknIds) {
        List<Finalist> finalists = finalists(tournament);
        Map<String, Finalist> finalistsByUser = finalists.stream()
                .collect(Collectors.toMap(Finalist::userUid, finalist -> finalist));
        Map<String, ArchonDeck> decksByUser = new HashMap<>();
        for (ArchonDeck deck : tournamentDecks) {
            if (!finalistsByUser.containsKey(deck.getUserUid())) {
                continue;
            }
            decksByUser.putIfAbsent(deck.getUserUid(), deck);
        }
        for (Map.Entry<String, ArchonDeck> entry : decksByUser.entrySet()) {
            parseDeck(tournament, finalistsByUser.get(entry.getKey()), entry.getValue(), veknIds);
        }
    }

    List<Finalist> finalists(ArchonTournament tournament) {
        List<Finalist> finalists = new ArrayList<>();
        List<ArchonTournament.Seat> seats = tournament.getFinals() != null && tournament.getFinals().getSeating() != null
                ? tournament.getFinals().getSeating() : List.of();
        boolean hasCompleteFinal = seats.size() == 5;
        for (int index = 0; index < seats.size(); index++) {
            ArchonTournament.Seat seat = seats.get(index);
            if (seat == null || StringUtils.isBlank(seat.getPlayerUid())) {
                hasCompleteFinal = false;
                continue;
            }
            ArchonTournament.Score score = seat.getResult();
            finalists.add(new Finalist(
                    seat.getPlayerUid(),
                    index + 1,
                    score != null ? score.getVp() : null,
                    score != null ? score.getTp() : null,
                    0
            ));
        }
        Finalist winner = finalists.stream().filter(finalist -> finalist.userUid().equals(tournament.getWinner())).findFirst().orElse(null);
        if (winner == null) {
            return List.of(new Finalist(tournament.getWinner(), null, null, null, 1));
        }
        if (!hasCompleteFinal || finalists.size() != 5) {
            return List.of(winner.withPosition(1));
        }
        List<Finalist> ranked = finalists.stream().filter(finalist -> finalist != winner)
                .sorted(Comparator.comparing(Finalist::vp, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Finalist::tp, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Finalist::seat, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<Finalist> results = new ArrayList<>();
        results.add(winner.withPosition(1));
        for (int index = 0; index < ranked.size(); index++) {
            results.add(ranked.get(index).withPosition(index + 2));
        }
        return results;
    }

    void parseDeck(ArchonTournament tournament, Finalist finalist, ArchonDeck source, Map<String, String> veknIds) {
        String eventId = getEventId(tournament);
        if (eventId == null) {
            log.warn("Skipping Archon deck {} because tournament {} has no event code", source.getUid(), tournament.getUid());
            return;
        }
        if (tournament.getStart() == null) {
            log.warn("Skipping Archon deck with missing tournament date for event {}", eventId);
            return;
        }
        String id = deckId(eventId, finalist.position());
        importPolicy.execute(id, eventId, finalist.position(), TournamentSchedulerOwner.ARCHON, (actual, action) -> {
            if (action == TournamentImportPolicy.Action.ENRICH_FINAL_RESULT) {
                updateTwdaFinalResult(actual, finalist);
            } else {
                importDeck(tournament, finalist, source, veknIds, actual);
            }
        });
    }

    private void importDeck(ArchonTournament tournament, Finalist finalist, ArchonDeck source,
                            Map<String, String> veknIds, DeckEntity actual) {
        String eventId = getEventId(tournament);
        String id = actual != null ? actual.getId() : deckId(eventId, finalist.position());

        DeckEntity deck = actual != null ? actual.toBuilder().build() : DeckEntity.builder().build();
        deck.setId(id);
        deck.setType(DeckType.TOURNAMENT);
        deck.setSchedulerOwner(TournamentSchedulerOwner.ARCHON);
        deck.setTournament(StringUtils.trimToNull(tournament.getName()));
        deck.setPlayers(getPlayers(tournament));
        deck.setRounds(getRounds(tournament));
        deck.setPlace(place(tournament));
        deck.setCountry(countryName(tournament.getCountry()));
        deck.setYear(tournament.getStart().getYear());
        String veknId = veknIds.get(finalist.userUid());
        String author = veknId;
        if (StringUtils.isNotBlank(veknId)) {
            author = archonUserRepository.findById(veknId)
                    .map(user -> StringUtils.defaultIfBlank(user.getName(), veknId)).orElse(veknId);
        }
        deck.setAuthor(author);
        deck.setUrl(tournamentUrl(tournament));
        deck.setEventId(eventId);
        deck.setFinalVp(finalist.vp());
        deck.setPosition(finalist.position());
        deck.setFinalSeat(finalist.seat());
        deck.setSource(ARCHON_TOURNAMENT_URL + tournament.getUid());
        deck.setViews(actual != null ? actual.getViews() : 0L);
        deck.setVerified(actual != null && actual.getVerified());
        boolean invalidSourceName = TournamentDeckName.isInvalid(source.getName());
        String name = TournamentDeckName.validName(source.getName());
        if (name == null && !invalidSourceName && actual != null) {
            name = StringUtils.trimToNull(actual.getName());
        }
        if (name != null) {
            deck.setName(name);
        } else {
            deck.setName(tournament.getName() + (finalist.position() > 1 ? ", Finalist #" + finalist.position() : StringUtils.EMPTY));
        }
        deck.setDescription(StringUtils.trimToNull(source.getComments()));
        if (actual != null && actual.getCreationDate() != null
                && actual.getCreationDate().toLocalDate().equals(tournament.getStart().toLocalDate())) {
            deck.setCreationDate(actual.getCreationDate());
        } else {
            deck.setCreationDate(tournament.getStart().toLocalDate().atStartOfDay());
        }
        if (hasTournamentResultConflict(deck)) {
            return;
        }

        Map<Integer, DeckCardEntity> deckCards = buildCards(id, source);
        if (!isValidDeck(deck, deckCards)) {
            return;
        }
        persist(actual, deck, deckCards);
    }

    private void updateTwdaFinalResult(DeckEntity deck, Finalist finalist) {
        boolean updated = false;
        if (finalist.vp() != null && !java.util.Objects.equals(deck.getFinalVp(), finalist.vp())) {
            deck.setFinalVp(finalist.vp());
            updated = true;
        }
        if (finalist.seat() != null && !java.util.Objects.equals(deck.getFinalSeat(), finalist.seat())) {
            deck.setFinalSeat(finalist.seat());
            updated = true;
        }
        if (updated) {
            deckRepository.saveAndFlush(deck);
        }
    }

    private Integer getRounds(ArchonTournament tournament) {
        // Preliminary rounds are an array of table arrays; the final is stored separately.
        if (tournament.getRounds() != null && !tournament.getRounds().isEmpty()) {
            return Math.min(3, tournament.getRounds().size());
        }
        return tournament.getMaxRounds() != null && tournament.getMaxRounds() > 0
                ? Math.min(3, tournament.getMaxRounds()) : null;
    }

    private Integer getPlayers(ArchonTournament tournament) {
        if (tournament.getPlayers() == null || tournament.getPlayers().isEmpty()) {
            return null;
        }
        return (int) tournament.getPlayers().stream().filter(player -> !Boolean.TRUE.equals(player.getNonCompeting())).count();
    }

    private String countryName(String countryCode) {
        String code = StringUtils.trimToNull(countryCode);
        if (code == null) {
            return null;
        }
        String name = new Locale("", code).getDisplayCountry(Locale.ENGLISH);
        return StringUtils.trimToNull(name);
    }

    private String place(ArchonTournament tournament) {
        List<String> components = new ArrayList<>();
        addPlaceComponent(components, tournament.getVenue());
        addPlaceComponent(components, tournament.getCity());
        addPlaceComponent(components, countryName(tournament.getCountry()));
        return components.isEmpty() ? null : String.join(", ", components);
    }

    private void addPlaceComponent(List<String> components, String value) {
        String component = StringUtils.trimToNull(value);
        if (component != null && components.stream().noneMatch(existing -> existing.equalsIgnoreCase(component))) {
            components.add(component);
        }
    }

    private String getEventId(ArchonTournament tournament) {
        if (tournament == null) {
            return null;
        }
        return StringUtils.trimToNull(tournament.getEventCode());
    }

    private String deckId(String eventId, int position) {
        if (position == 1) {
            return "tournament-" + eventId;
        }
        return "tournament-" + eventId + "-" + position;
    }

    private String tournamentUrl(ArchonTournament tournament) {
        String veknEventId = tournament.getExternalIds() != null
                ? StringUtils.trimToNull(tournament.getExternalIds().getVekn()) : null;
        if (veknEventId != null) {
            return VEKN_EVENT_URL + veknEventId;
        }
        return ARCHON_TOURNAMENT_URL + tournament.getUid();
    }

    private Map<String, String> loadVeknIds(Path export, Set<String> finalistUids) throws Exception {
        if (finalistUids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (ArchonUser user : readUsers(export)) {
            if (finalistUids.contains(user.getUid()) && StringUtils.isNotBlank(user.getVeknId())) {
                result.put(user.getUid(), user.getVeknId());
            }
        }
        return result;
    }

    private Path downloadExport(String authorization) throws Exception {
        Path export = Files.createTempFile("archon-export-", ".jsonl.gz");
        try (Response response = archonClient.export(authorization)) {
            if (response.body() == null) {
                throw new IOException("Archon export response has no body");
            }
            try (InputStream stream = response.body().asInputStream()) {
                Files.copy(stream, export, StandardCopyOption.REPLACE_EXISTING);
            }
            return export;
        } catch (Exception e) {
            Files.deleteIfExists(export);
            throw e;
        }
    }

    private void deleteExport(Path export) {
        if (export == null) {
            return;
        }
        try {
            Files.deleteIfExists(export);
        } catch (IOException e) {
            log.warn("Unable to delete temporary Archon export {}", export, e);
        }
    }

    List<ArchonTournament> readTournaments(Path export) throws Exception {
        List<ArchonTournament> tournaments = new ArrayList<>();
        readExport(export, (type, data) -> {
            if ("tournament".equals(type)) {
                tournaments.add(jsonMapper.treeToValue(data, ArchonTournament.class));
            }
        });
        return tournaments;
    }

    private List<ArchonUser> readUsers(Path export) throws Exception {
        List<ArchonUser> users = new ArrayList<>();
        readExport(export, (type, data) -> {
            if ("user".equals(type)) {
                users.add(jsonMapper.treeToValue(data, ArchonUser.class));
            }
        });
        return users;
    }

    private Map<String, List<ArchonDeck>> loadTournamentDecks(Path export, List<ArchonTournament> tournaments) throws Exception {
        Set<String> tournamentUids = tournaments.stream().map(ArchonTournament::getUid).collect(Collectors.toSet());
        Map<String, List<ArchonDeck>> decksByTournament = new HashMap<>();
        readExport(export, (type, data) -> {
            if (!"deck".equals(type)) {
                return;
            }
            ArchonDeck deck = jsonMapper.treeToValue(data, ArchonDeck.class);
            if (tournamentUids.contains(deck.getTournamentUid())) {
                decksByTournament.computeIfAbsent(deck.getTournamentUid(), key -> new ArrayList<>()).add(deck);
            }
        });
        return decksByTournament;
    }

    private void readExport(Path export, ExportRecordConsumer consumer) throws Exception {
        try (InputStream file = Files.newInputStream(export);
             GZIPInputStream gzip = new GZIPInputStream(file);
             BufferedReader reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (StringUtils.isBlank(line)) {
                    continue;
                }
                JsonNode record = jsonMapper.readTree(line);
                JsonNode data = record.path("data");
                if (!data.isMissingNode() && !data.isNull()) {
                    consumer.accept(record.path("type").asText(), data);
                }
            }
        }
    }

    private Map<Integer, DeckCardEntity> buildCards(String deckId, ArchonDeck source) {
        Map<Integer, DeckCardEntity> result = new HashMap<>();
        if (source.getCards() == null) {
            return result;
        }
        for (Map.Entry<Integer, Integer> entry : source.getCards().entrySet()) {
            Integer cardId = entry.getKey();
            Integer quantity = entry.getValue();
            if (cardId == null || quantity == null || quantity <= 0 || !existsCard(cardId)) {
                log.error("Unknown or invalid card {} on Archon deck {}", cardId, deckId);
                continue;
            }
            DeckCardEntity card = new DeckCardEntity();
            card.setId(new DeckCardEntity.DeckCardId());
            card.getId().setDeckId(deckId);
            card.getId().setCardId(cardId);
            card.setNumber(quantity);
            result.put(cardId, card);
        }
        return result;
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
            return true;
        }
        log.error("Invalid number of cards for Archon deck {}. Crypt {} Library {}", deck.getId(), crypt, library);
        return false;
    }

    private boolean hasTournamentResultConflict(DeckEntity deck) {
        boolean exists = deckRepository.existsByTypeAndEventIdAndPositionAndIdNotAndDeletedFalse(
                DeckType.TOURNAMENT, deck.getEventId(), deck.getPosition(), deck.getId());
        if (exists) {
            log.warn("Skipping Archon deck {}, event {} already has a deck in position {}", deck.getId(), deck.getEventId(), deck.getPosition());
        }
        return exists;
    }

    private void persist(DeckEntity actual, DeckEntity deck, Map<Integer, DeckCardEntity> deckCards) {
        boolean insert = actual == null;
        if (insert || !actual.equals(deck) || !java.util.Objects.equals(actual.getCreationDate(), deck.getCreationDate())) {
            deckRepository.saveAndFlush(deck);
        }
        List<DeckCardEntity> dbCards = deckCardRepository.findByIdDeckId(deck.getId());
        for (DeckCardEntity card : deckCards.values()) {
            DeckCardEntity current = dbCards.stream()
                    .filter(dbCard -> dbCard.getId().getCardId().equals(card.getId().getCardId()))
                    .findFirst().orElse(null);
            if (current == null || !current.equals(card)) {
                deckCardRepository.saveAndFlush(card);
            }
        }
        for (DeckCardEntity card : dbCards) {
            if (!deckCards.containsKey(card.getId().getCardId())) {
                deckCardRepository.deleteById(card.getId());
            }
        }
    }

    private String fetchToken() {
        org.springframework.util.LinkedMultiValueMap<String, String> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        String accessToken = StringUtils.trimToNull(archonClient.token(form).getAccessToken());
        if (accessToken == null) {
            throw new IllegalStateException("Archon token response did not contain an access token");
        }
        return accessToken;
    }

    @FunctionalInterface
    private interface ExportRecordConsumer {
        void accept(String type, JsonNode data) throws Exception;
    }

    record Finalist(String userUid, Integer seat, BigDecimal vp, Integer tp, int position) {
        Finalist withPosition(int newPosition) {
            return new Finalist(userUid, seat, vp, tp, newPosition);
        }
    }
}
