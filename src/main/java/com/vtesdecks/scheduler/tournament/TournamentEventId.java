package com.vtesdecks.scheduler.tournament;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts event identifiers only from supported official VEKN and Archon event URLs. */
final class TournamentEventId {
    private static final List<Pattern> URL_PATTERNS = List.of(
            Pattern.compile("^https?://(?:www\\.)?vekn\\.net/event-calendar/event/([0-9]+)/?$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^https?://archon\\.vekn\\.net/t/([A-Za-z0-9-]+)/?$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^https?://archon\\.vekn\\.net/tournament/([A-Za-z0-9-]+)/display\\.html/?$", Pattern.CASE_INSENSITIVE)
    );

    private TournamentEventId() {
    }

    static String fromUrl(String url) {
        String normalized = StringUtils.trimToNull(url);
        if (normalized == null) {
            return null;
        }
        for (Pattern pattern : URL_PATTERNS) {
            Matcher matcher = pattern.matcher(normalized);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }
}
