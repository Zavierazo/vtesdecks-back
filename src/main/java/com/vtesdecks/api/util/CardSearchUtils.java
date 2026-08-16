package com.vtesdecks.api.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class CardSearchUtils {
    public static final int MAX_CARD_IDS = 6000;
    private static final String CARD_ID = "cardId";

    private CardSearchUtils() {
    }

    /**
     * Converts the JSON search filters (values keep their native JSON types) into the same
     * string-valued parameter map used by the GET list endpoints. Null/empty values are skipped
     * and collections are joined with commas.
     */
    public static Map<String, String> toParams(Map<String, Object> filters) {
        Map<String, String> params = new HashMap<>();
        if (filters == null) {
            return params;
        }
        filters.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            String stringValue;
            if (value instanceof Collection<?> collection) {
                stringValue = collection.stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
            } else {
                stringValue = String.valueOf(value);
            }
            if (StringUtils.isNotEmpty(stringValue)) {
                params.put(key, stringValue);
            }
        });
        return params;
    }

    /**
     * AND-combines the cardIds restriction with any cardId filter already present.
     * Returns false when the restriction cannot match any row (empty id list or empty
     * intersection), so the caller must return an empty page instead of querying.
     */
    public static boolean applyCardIds(Map<String, String> filters, List<Integer> cardIds) {
        if (cardIds == null) {
            return true;
        }
        if (cardIds.size() > MAX_CARD_IDS) {
            throw new IllegalArgumentException("Too many card ids, maximum is " + MAX_CARD_IDS);
        }
        Set<Integer> ids = cardIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String existing = filters.get(CARD_ID);
        if (existing != null) {
            if (existing.isEmpty()) {
                return false;
            }
            Set<Integer> existingIds = Arrays.stream(existing.split(","))
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .collect(Collectors.toSet());
            ids.retainAll(existingIds);
        }
        if (ids.isEmpty()) {
            return false;
        }
        filters.put(CARD_ID, ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
        return true;
    }
}
