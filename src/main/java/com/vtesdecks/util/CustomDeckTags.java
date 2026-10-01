package com.vtesdecks.util;

import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class CustomDeckTags {
    private CustomDeckTags() {}

    public static void validate(List<String> tags) {
        // Missing/null fields from older clients must preserve existing tags.
        if (tags == null) {
            return;
        }
        if (tags.size() > 3 || tags.stream().anyMatch(tag -> tag == null || !tag.matches("[a-z0-9]{1,10}"))
                || new HashSet<>(tags).size() != tags.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use at most 3 unique custom tags, each 1-10 lowercase letters or digits");
        }
    }
}
