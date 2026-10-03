package com.vtesdecks.service;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.model.ArchetypeAttributeRequirement.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;

/** Quantity-weighted catalog attributes, computed once per candidate deck. */
public final class ArchetypeAttributeCounter {
    private ArchetypeAttributeCounter() {}

    public record Key(Type type, String value) {}

    public static Map<Key, Long> count(Map<Integer, Integer> cards, CryptCache cryptCache, LibraryCache libraryCache) {
        Map<Key, Long> counts = new HashMap<>();
        cards.forEach((id, quantity) -> {
            if (quantity == null || quantity <= 0) {
                return;
            }
            var crypt = com.vtesdecks.util.VtesUtils.isCrypt(id) ? cryptCache.get(id) : null;
            if (crypt != null) {
                if (crypt.getClan() != null) {
                    counts.merge(new Key(Type.CRYPT_CLAN, crypt.getClan()), quantity.longValue(), Long::sum);
                }
                add(counts, Type.CRYPT_DISCIPLINE, crypt.getDisciplines(), quantity);
            }
            var library = com.vtesdecks.util.VtesUtils.isCrypt(id) ? null : libraryCache.get(id);
            if (library != null) {
                add(counts, Type.LIBRARY_TYPE, library.getTypes(), quantity);
                add(counts, Type.LIBRARY_DISCIPLINE, library.getDisciplines(), quantity);
            }
        });
        return counts;
    }

    private static void add(Map<Key, Long> counts, Type type, Collection<String> values, int quantity) {
        if (values != null) {
            values.stream().filter(java.util.Objects::nonNull).distinct()
                    .forEach(value -> counts.merge(new Key(type, value), (long) quantity, Long::sum));
        }
    }
}
