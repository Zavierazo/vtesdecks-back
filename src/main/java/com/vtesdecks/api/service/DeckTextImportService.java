package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.model.api.ApiBaseCard;
import com.vtesdecks.model.api.ApiCard;
import com.vtesdecks.model.api.ApiCrypt;
import com.vtesdecks.model.api.ApiLibrary;
import com.vtesdecks.model.api.ApiDeckBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
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
     * TWD crypt line: {@code "3x  Victoria Ash     5 ANI FOR ..."}
     * <p>The name sits between {@code "{N}x "} and the multi-space padding that
     * precedes the capacity digit. Only one space is required after the capacity
     * (disciplines follow with a single space separator in practice).
     */
    private static final Pattern TWD_CRYPT_CARD_PATTERN =
            Pattern.compile("^(\\d+)x\\s+(.*?)\\s+\\d+\\s+");

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

    /**
     * {@code "Deck Name: My Deck"}
     */
    private static final Pattern DECK_NAME_PATTERN =
            Pattern.compile("(?i)^Deck Name:\\s*(.+)$");

    /**
     * {@code "Description: Some text"}
     */
    private static final Pattern DESCRIPTION_PATTERN =
            Pattern.compile("(?i)^Description:\\s*(.*)$");

    /**
     * Suffix appended to advanced vampire names in all export formats.
     */
    private static final String ADV_SUFFIX = "(ADV)";

    /** Avoid accepting weak fuzzy matches while still tolerating small typos. */
    private static final double FUZZY_MIN_SCORE = 0.5;
    private static final int FUZZY_RESULT_LIMIT = 10;
    private static final Set<String> FUZZY_RESULT_FIELDS = Set.of("id", "name", "adv", "group");

    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;
    private final ApiCardService apiCardService;

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

        // Prefer safe exact matches; use the general card search only as fallback.
        List<ResolvedCryptEntry> cryptEntries = new ArrayList<>();
        for (CardEntry entry : parsedEntries) {
            List<Crypt> cryptCandidates = getExactCryptCandidates(entry);
            if (!cryptCandidates.isEmpty()) {
                cryptEntries.add(new ResolvedCryptEntry(entry, cryptCandidates));
                continue;
            }

            Library library = getExactLibraryCandidate(entry.name());
            if (library != null) {
                addLibraryCard(builder, library, entry.count());
                continue;
            }

            List<ApiBaseCard> fuzzyCandidates = getBestFuzzyCandidates(entry);
            if (!fuzzyCandidates.isEmpty() && fuzzyCandidates.getFirst() instanceof ApiCrypt) {
                List<Crypt> fuzzyCryptCandidates = fuzzyCandidates.stream()
                        .filter(ApiCrypt.class::isInstance)
                        .map(ApiBaseCard::getId)
                        .map(cryptCache::get)
                        .filter(java.util.Objects::nonNull)
                        .toList();
                if (!fuzzyCryptCandidates.isEmpty()) {
                    cryptEntries.add(new ResolvedCryptEntry(entry, fuzzyCryptCandidates));
                    continue;
                }
            } else if (!fuzzyCandidates.isEmpty() && fuzzyCandidates.getFirst() instanceof ApiLibrary) {
                Library fuzzyLibrary = libraryCache.get(fuzzyCandidates.getFirst().getId());
                if (fuzzyLibrary != null) {
                    addLibraryCard(builder, fuzzyLibrary, entry.count());
                    continue;
                }
            }

            log.warn("Card not found during text import: '{}'", entry.name());
        }

        // Resolve crypt cards with group disambiguation and add to builder
        resolveAndAddCryptCards(builder, cryptEntries);

        return builder.getCards().isEmpty() ? null : builder;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<Crypt> getExactCryptCandidates(CardEntry entry) {
        boolean isAdv = entry.name().endsWith(ADV_SUFFIX);
        String searchName = isAdv
                ? entry.name().substring(0, entry.name().lastIndexOf(ADV_SUFFIX)).trim()
                : entry.name();

        for (String variant : getNameVariants(searchName)) {
            List<Crypt> candidates = new ArrayList<>();
            try (ResultSet<Crypt> rs = cryptCache.selectByExactName(variant)) {
                for (Crypt crypt : rs) {
                    if (crypt.isAdv() == isAdv) {
                        candidates.add(crypt);
                    }
                }
            }
            if (!candidates.isEmpty()) {
                return candidates;
            }
        }

        for (String variant : getNameVariants(searchName)) {
            List<Crypt> candidates = new ArrayList<>();
            try (ResultSet<Crypt> rs = cryptCache.selectByExactI18nName(variant)) {
                for (Crypt crypt : rs) {
                    if (crypt.isAdv() == isAdv) {
                        candidates.add(crypt);
                    }
                }
            }
            if (!candidates.isEmpty()) {
                return candidates;
            }
        }
        return Collections.emptyList();
    }

    private Library getExactLibraryCandidate(String name) {
        for (String variant : getNameVariants(name)) {
            try (ResultSet<Library> results = libraryCache.selectByExactName(variant)) {
                if (results.isNotEmpty()) {
                    return results.stream().findFirst().orElse(null);
                }
            }
        }
        for (String variant : getNameVariants(name)) {
            try (ResultSet<Library> results = libraryCache.selectByExactI18nName(variant)) {
                if (results.isNotEmpty()) {
                    return results.stream().findFirst().orElse(null);
                }
            }
        }
        return null;
    }

    private List<ApiBaseCard> getBestFuzzyCandidates(CardEntry entry) {
        boolean isAdv = entry.name().endsWith(ADV_SUFFIX);
        String searchName = isAdv
                ? entry.name().substring(0, entry.name().lastIndexOf(ADV_SUFFIX)).trim()
                : entry.name();
        List<ApiBaseCard> results = apiCardService.searchCards(
                searchName, FUZZY_MIN_SCORE, FUZZY_RESULT_LIMIT, FUZZY_RESULT_FIELDS);
        if (isAdv) {
            results = results.stream()
                    .filter(ApiCrypt.class::isInstance)
                    .filter(card -> Boolean.TRUE.equals(((ApiCrypt) card).getAdv()))
                    .toList();
        } else {
            results = results.stream()
                    .filter(card -> !(card instanceof ApiCrypt crypt) || !Boolean.TRUE.equals(crypt.getAdv()))
                    .toList();
        }
        if (results.isEmpty()) {
            return Collections.emptyList();
        }

        double bestScore = getScore(results.getFirst());
        return results.stream()
                .filter(card -> Double.compare(getScore(card), bestScore) == 0)
                .toList();
    }

    private double getScore(ApiBaseCard card) {
        return card instanceof ApiCrypt crypt ? crypt.getScore() : ((ApiLibrary) card).getScore();
    }

    private List<String> getNameVariants(String name) {
        String trimmedName = name.trim();
        if (trimmedName.regionMatches(true, 0, "The ", 0, 4)) {
            return List.of(trimmedName, trimmedName.substring(4) + ", The");
        }
        if (trimmedName.length() >= 5
                && trimmedName.regionMatches(true, trimmedName.length() - 5, ", The", 0, 5)) {
            return List.of(trimmedName, "The " + trimmedName.substring(0, trimmedName.length() - 5));
        }
        return List.of(trimmedName);
    }

    private void addLibraryCard(ApiDeckBuilder builder, Library library, int count) {
        builder.getCards().add(buildApiCard(library.getId(), count));
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
    private void resolveAndAddCryptCards(ApiDeckBuilder builder, List<ResolvedCryptEntry> cryptEntries) {
        Map<Integer, List<Crypt>> candidatesMap = new LinkedHashMap<>();
        Set<Integer> resolvedGroups = new HashSet<>();

        // First pass: build candidate lists and extract unambiguous groups
        for (int i = 0; i < cryptEntries.size(); i++) {
            List<Crypt> candidates = cryptEntries.get(i).candidates();
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
            CardEntry entry = cryptEntries.get(i).entry();
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

    private ApiCard buildApiCard(Integer id, int count) {
        ApiCard card = new ApiCard();
        card.setId(id);
        card.setNumber(count);
        Library library = libraryCache.get(id);
        card.setType(library != null ? library.getType() : null);
        return card;
    }

    private record CardEntry(int count, String name) {
    }

    private record ResolvedCryptEntry(CardEntry entry, List<Crypt> candidates) {
    }
}




