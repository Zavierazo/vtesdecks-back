package com.vtesdecks.util;

import com.vtesdecks.jpa.entity.converter.StringListConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CustomDeckTagsTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "ABC", "a b", "a,b", "a-b", "á", "abcdefghijk", "abc\n"})
    void rejectsInvalidTags(String tag) {
        assertThrows(ResponseStatusException.class, () -> CustomDeckTags.validate(List.of(tag)));
    }

    @Test
    void checksLimitsDuplicatesAndLegacyMissingValues() {
        assertDoesNotThrow(() -> CustomDeckTags.validate(null));
        assertDoesNotThrow(() -> CustomDeckTags.validate(List.of()));
        assertDoesNotThrow(() -> CustomDeckTags.validate(List.of("a", "0123456789", "league")));
        assertThrows(ResponseStatusException.class, () -> CustomDeckTags.validate(List.of("a", "a")));
        assertThrows(ResponseStatusException.class, () -> CustomDeckTags.validate(List.of("a", "b", "c", "d")));
        assertThrows(ResponseStatusException.class, () -> CustomDeckTags.validate(Arrays.asList("a", null)));
    }

    @Test
    void jsonPersistencePreservesOrderAndTreatsLegacyNullAsEmpty() {
        StringListConverter converter = new StringListConverter();
        assertEquals(List.of(), converter.convertToEntityAttribute(null));
        assertEquals(List.of("z", "a"), converter.convertToEntityAttribute(converter.convertToDatabaseColumn(List.of("z", "a"))));
        assertEquals(List.of(), converter.convertToEntityAttribute(converter.convertToDatabaseColumn(List.of())));
    }
}
