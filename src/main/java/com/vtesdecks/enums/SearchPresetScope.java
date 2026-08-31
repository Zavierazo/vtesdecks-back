package com.vtesdecks.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum SearchPresetScope {
    CRYPT,
    LIBRARY,
    DECKS;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static SearchPresetScope fromJson(String value) {
        return value == null ? null : valueOf(value.toUpperCase(Locale.ROOT));
    }
}
