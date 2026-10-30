package com.vtesdecks.scheduler.tournament.helpers;

import com.vtesdecks.jpa.repositories.ArchonUserRepository.PlayerName;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** A read-only name lookup built once per import, safe to share between workers. */
public final class TournamentPlayerLookup {
    private final Map<String, Set<String>> idsByName;

    public TournamentPlayerLookup(Collection<? extends PlayerName> players) {
        Map<String, Set<String>> names = new HashMap<>();
        for (PlayerName player : players) {
            String name = normalize(player.getName());
            if (!name.isEmpty() && StringUtils.isNotBlank(player.getVeknId())) {
                names.computeIfAbsent(name, ignored -> new HashSet<>()).add(player.getVeknId());
            }
        }
        names.replaceAll((name, ids) -> Set.copyOf(ids));
        idsByName = Map.copyOf(names);
    }

    public String findVeknId(String author) {
        String name = normalize(author);
        Set<String> exact = idsByName.get(name);
        if (exact != null) {
            return exact.size() == 1 ? exact.iterator().next() : null;
        }
        if (!name.contains(" ")) {
            return null;
        }
        String fullName = " " + name + " ";
        String match = null;
        for (var entry : idsByName.entrySet()) {
            String candidate = " " + entry.getKey() + " ";
            if (entry.getKey().contains(" ") && (fullName.contains(candidate) || candidate.contains(fullName)
                    || hasOrderedPrefixes(name, entry.getKey()))) {
                for (String id : entry.getValue()) {
                    if (match != null && !match.equals(id)) {
                        return null;
                    }
                    match = id;
                }
            }
        }
        return match;
    }

    private static boolean hasOrderedPrefixes(String name, String candidate) {
        String[] words = candidate.split(" ");
        int next = 0;
        for (String part : name.split(" ")) {
            while (next < words.length && !words[next].startsWith(part)) {
                next++;
            }
            if (next == words.length) {
                return false;
            }
            next++;
        }
        return true;
    }

    private static String normalize(String name) {
        return StringUtils.normalizeSpace(StringUtils.stripAccents(StringUtils.defaultString(name))).toLowerCase(Locale.ROOT);
    }
}
