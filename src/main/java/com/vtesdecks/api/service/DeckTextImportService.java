package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.model.api.ApiCard;
import com.vtesdecks.model.api.ApiDeckBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses a VTES deck from plain text in any of the supported formats (LACKEY,
 * JOL, TWD) without explicit format detection.
 *
 * <p>Each line is tested against a prioritised set of patterns; lines that do
 * not match any pattern are silently ignored. Card names are resolved against
 * the crypt cache first and, if not found there, against the library cache.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeckTextImportService {

    /**
     * TWD crypt line: {@code "3x  Victoria Ash     5  ANI FOR ..."}
     * <p>The name sits between {@code "{N}x "} and the double-space separator
     * that precedes the capacity digit.
     */
    private static final Pattern TWD_CRYPT_CARD_PATTERN =
            Pattern.compile("^(\\d+)x\\s+(.*?)\\s{2,}\\d+\\s{2,}");

    /**
     * Generic card line that covers all three formats:
     * <ul>
     *   <li>LACKEY:      {@code "3<TAB>Card Name"}</li>
     *   <li>JOL:         {@code "3xCard Name"}</li>
     *   <li>TWD library: {@code "3x Card Name"}</li>
     * </ul>
     */
    private static final Pattern GENERIC_CARD_PATTERN =
            Pattern.compile("^(\\d+)[\\tx]\\s*(.+)$");

    /** {@code "Deck Name: My Deck"} */
    private static final Pattern DECK_NAME_PATTERN =
            Pattern.compile("(?i)^Deck Name:\\s*(.+)$");

    /** {@code "Description: Some text"} */
    private static final Pattern DESCRIPTION_PATTERN =
            Pattern.compile("(?i)^Description:\\s*(.*)$");

    /** Suffix appended to advanced vampire names in all export formats. */
    private static final String ADV_SUFFIX = "(ADV)";

    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Parses {@code text} and returns an unsaved {@link ApiDeckBuilder}.
     * Returns {@code null} if no cards could be resolved.
     */
    public ApiDeckBuilder importFromText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        ApiDeckBuilder builder = new ApiDeckBuilder();
        builder.setPublished(false);
        builder.setCards(new ArrayList<>());

        String[] lines = text.strip().split("\\r?\\n");
        String deckName = null;
        StringBuilder description = new StringBuilder();
        List<CardEntry> parsedEntries = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();

            // 1. Deck Name metadata
            Matcher nameMatcher = DECK_NAME_PATTERN.matcher(trimmed);
            if (nameMatcher.matches()) {
                deckName = nameMatcher.group(1);
                continue;
            }

            // 2. Description metadata
            Matcher descMatcher = DESCRIPTION_PATTERN.matcher(trimmed);
            if (descMatcher.matches()) {
                String val = descMatcher.group(1).trim();
                if (!val.isEmpty()) {
                    description.append(val);
                }
                continue;
            }

            // 3. TWD crypt line (specific: padded name followed by capacity)
            //    Must be tested before the generic pattern to avoid capturing
            //    the padding and capacity as part of the card name.
            Matcher twdMatcher = TWD_CRYPT_CARD_PATTERN.matcher(line);
            if (twdMatcher.find()) {
                parsedEntries.add(new CardEntry(
                        Integer.parseInt(twdMatcher.group(1)),
                        twdMatcher.group(2).trim()));
                continue;
            }

            // 4. Generic card line (LACKEY tab / JOL "Nx" / TWD library "Nx name")
            Matcher genericMatcher = GENERIC_CARD_PATTERN.matcher(trimmed);
            if (genericMatcher.matches()) {
                parsedEntries.add(new CardEntry(
                        Integer.parseInt(genericMatcher.group(1)),
                        genericMatcher.group(2).trim()));
                continue;
            }

            // 5. Line does not match any expected pattern → ignore
            log.debug("Ignoring unmatched line during text import: '{}'", trimmed);
        }

        builder.setName(deckName);
        if (!description.isEmpty()) {
            builder.setDescription(description.toString());
        }

        // Classify each parsed entry as crypt or library by probing the caches
        List<CardEntry> cryptEntries = new ArrayList<>();
        for (CardEntry entry : parsedEntries) {
            if (hasCryptCandidates(entry)) {
                cryptEntries.add(entry);
            } else {
                addLibraryCard(builder, entry.name(), entry.count());
            }
        }

        // Resolve crypt cards with group disambiguation and add to builder
        resolveAndAddCryptCards(builder, cryptEntries);

        return builder.getCards().isEmpty() ? null : builder;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the entry's name resolves to at least one crypt
     * card (respecting the {@code (ADV)} suffix).
     */
    private boolean hasCryptCandidates(CardEntry entry) {
        boolean isAdv = entry.name().endsWith(ADV_SUFFIX);
        String searchName = isAdv
                ? entry.name().substring(0, entry.name().lastIndexOf(ADV_SUFFIX)).trim()
                : entry.name();
        try (ResultSet<Crypt> rs = cryptCache.selectByExactName(searchName)) {
            for (Crypt c : rs) {
                if (c.isAdv() == isAdv) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addLibraryCard(ApiDeckBuilder builder, String name, int count) {
        try (ResultSet<Library> results = libraryCache.selectByExactName(name)) {
            results.stream().findFirst().ifPresentOrElse(
                    library -> builder.getCards().add(buildApiCard(library.getId(), count)),
                    () -> log.warn("Card not found during text import: '{}'", name)
            );
        }
    }

    /**
     * Resolves crypt entries to card IDs using a two-pass group disambiguation
     * strategy:
     * <ol>
     *   <li>Collect the groups of unambiguously resolved (single-result) vampires.</li>
     *   <li>For entries with multiple candidates, filter by those groups; use
     *       the filtered set if it narrows to one result, otherwise include all
     *       candidates.</li>
     * </ol>
     * The {@code ANY} group (value {@literal < 0}) is excluded from group
     * filtering but included as a candidate if no normal-group match exists.
     */
    private void resolveAndAddCryptCards(ApiDeckBuilder builder, List<CardEntry> cryptEntries) {
        Map<Integer, List<Crypt>> candidatesMap = new LinkedHashMap<>();
        Set<Integer> resolvedGroups = new HashSet<>();

        // First pass: build candidate lists and extract unambiguous groups
        for (int i = 0; i < cryptEntries.size(); i++) {
            List<Crypt> candidates = getCryptCandidates(cryptEntries.get(i));
            candidatesMap.put(i, candidates);
            if (candidates.size() == 1) {
                Crypt c = candidates.getFirst();
                if (c.getGroup() != null && c.getGroup() >= 0) {
                    resolvedGroups.add(c.getGroup());
                }
            }
        }

        // Second pass: resolve ambiguous entries using the collected groups
        for (int i = 0; i < cryptEntries.size(); i++) {
            CardEntry entry = cryptEntries.get(i);
            List<Crypt> candidates = candidatesMap.get(i);

            if (candidates.isEmpty()) {
                log.warn("Crypt card not found during text import: '{}'", entry.name());
                continue;
            }

            List<Crypt> resolved;
            if (candidates.size() == 1) {
                resolved = candidates;
            } else {
                List<Crypt> groupFiltered = candidates.stream()
                        .filter(c -> c.getGroup() != null
                                && c.getGroup() >= 0
                                && resolvedGroups.contains(c.getGroup()))
                        .toList();

                if (groupFiltered.size() == 1) {
                    resolved = groupFiltered;
                } else if (!groupFiltered.isEmpty()) {
                    log.warn("Multiple group-filtered candidates for '{}': {}",
                            entry.name(),
                            groupFiltered.stream()
                                    .map(c -> c.getName() + " G" + c.getGroup())
                                    .collect(Collectors.joining(", ")));
                    resolved = groupFiltered;
                } else {
                    log.warn("Could not resolve group for '{}', including {} candidates",
                            entry.name(), candidates.size());
                    resolved = candidates;
                }
            }

            for (Crypt crypt : resolved) {
                builder.getCards().add(buildApiCard(crypt.getId(), entry.count()));
            }
        }
    }

    private List<Crypt> getCryptCandidates(CardEntry entry) {
        boolean isAdv = entry.name().endsWith(ADV_SUFFIX);
        String searchName = isAdv
                ? entry.name().substring(0, entry.name().lastIndexOf(ADV_SUFFIX)).trim()
                : entry.name();
        List<Crypt> candidates = new ArrayList<>();
        try (ResultSet<Crypt> rs = cryptCache.selectByExactName(searchName)) {
            for (Crypt c : rs) {
                if (c.isAdv() == isAdv) {
                    candidates.add(c);
                }
            }
        }
        return candidates;
    }

    private ApiCard buildApiCard(Integer id, int count) {
        ApiCard card = new ApiCard();
        card.setId(id);
        card.setNumber(count);
        Library library = libraryCache.get(id);
        card.setType(library != null ? library.getType() : null);
        return card;
    }

    private record CardEntry(int count, String name) {}
}




