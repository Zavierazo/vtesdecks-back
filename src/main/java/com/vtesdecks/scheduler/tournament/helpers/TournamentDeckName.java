package com.vtesdecks.scheduler.tournament.helpers;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

public final class TournamentDeckName {
    private static final Pattern CARD_SECTION = Pattern.compile("^(?:crypt|library)\\s*\\(", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATOR = Pattern.compile("^={10,}$");

    private TournamentDeckName() {
    }

    public static String validName(String name) {
        String trimmed = StringUtils.trimToNull(name);
        if (isInvalid(trimmed)) {
            return null;
        }
        return trimmed;
    }

    public static boolean isInvalid(String name) {
        String trimmed = StringUtils.trimToNull(name);
        return trimmed != null && (CARD_SECTION.matcher(trimmed).find() || SEPARATOR.matcher(trimmed).matches());
    }
}
