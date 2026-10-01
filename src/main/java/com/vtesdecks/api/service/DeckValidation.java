package com.vtesdecks.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.jpa.repositories.LimitedFormatRepository;
import com.vtesdecks.util.VtesUtils;
import com.vtesdecks.model.api.ApiCard;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** Publication rules mirror the builder, using caller-supplied cards and current catalogs. */
@Service
@RequiredArgsConstructor
public class DeckValidation {
    private final CryptCache crypt;
    private final LibraryCache library;
    private final LimitedFormatRepository formats;
    private final ObjectMapper mapper;

    // A diagnostic lookup must not mark the builder save transaction rollback-only.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean isValid(String name, JsonNode extra, List<ApiCard> submittedCards) {
        Map<Integer, ApiCard> savedCards = new LinkedHashMap<>();
        for (ApiCard card : submittedCards) {
            if (card != null && card.getNumber() != null) {
                savedCards.put(card.getId(), card);
            }
        }
        return validate(name, extra, new ArrayList<>(savedCards.values()));
    }

    private boolean validate(String name, JsonNode extra, List<ApiCard> deckCards) {
        if (name == null || name.isBlank()) { return false; }
        JsonNode format = extra == null ? null : extra.get("limitedFormat");
        if (format != null && format.hasNonNull("id")) {
            var current = formats.findById(format.get("id").asInt());
            if (current.isEmpty()) { return false; }
            format = mapper.valueToTree(current.get().getFormat());
        }
        long cryptSize = 0;
        long librarySize = 0;
        Set<Integer> groups = new HashSet<>();
        for (var row : deckCards) {
            Integer quantity = row.getNumber();
            if (quantity == null || quantity < 0) { return false; }
            if (quantity == 0) { continue; }
            Integer id = row.getId();
            if (id == null) { return false; }
            if (VtesUtils.isCrypt(id)) {
                var card = crypt.get(id);
                if (card == null || (card.getBanned() != null && !card.getBanned().isEmpty())) { return false; }
                if (!allowed(format, "crypt", id, card.getSets())) { return false; }
                cryptSize += quantity;
                if (card.getGroup() > 0) { groups.add(card.getGroup()); }
            } else {
                var card = library.get(id);
                if (card == null || (card.getBanned() != null && !card.getBanned().isEmpty())) { return false; }
                if (!allowed(format, "library", id, card.getSets())) { return false; }
                librarySize += quantity;
            }
        }
        if (groups.size() > 2 || (groups.size() == 2 && Collections.max(groups) - Collections.min(groups) > 1)) { return false; }
        return cryptSize >= limit(format, "minCrypt", 12) && cryptSize <= limit(format, "maxCrypt", Integer.MAX_VALUE)
                && librarySize >= limit(format, "minLibrary", 60) && librarySize <= limit(format, "maxLibrary", 90);
    }

    private int limit(JsonNode format, String field, int fallback) {
        return format != null && format.hasNonNull(field) ? format.get(field).asInt() : fallback;
    }

    private boolean allowed(JsonNode format, String type, Integer id, List<String> sets) {
        if (format == null || format.isNull()) { return true; }
        String key = id.toString();
        if (format.path("allowed").path(type).path(key).asBoolean(false)) { return true; }
        if (format.path("banned").path(type).path(key).asBoolean(false)) { return false; }
        return sets != null && sets.stream().anyMatch(set -> format.path("sets").has(set.split(":")[0]));
    }
}
