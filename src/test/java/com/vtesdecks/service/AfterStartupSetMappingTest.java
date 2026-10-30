package com.vtesdecks.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AfterStartupSetMappingTest {
    private final AfterStartupService service = new AfterStartupService();

    @Test
    void splitsBothReleaseProductsAndRetainsQuantities() {
        assertEquals(Set.of("V5SN:SoNY2"), sets("V5:SoNY2"));
        assertEquals(Set.of("V5SN:NBr4"), sets("V5:NBr4"));
        assertEquals(Set.of("V5SN:SoNY2", "V5SN:NBr1", "V5:PM3/PV1", "Jyhad:C"),
                sets("Jyhad:C, V5:PM3/SoNY2/NBr1/PV1"));
    }

    @Test
    void preservesOtherFifthEditionSplitsWithoutLeakingConvertedSubsets() {
        assertEquals(Set.of("V5SN:SoNY2", "V5SN:NBr1", "V5H:3", "V5L:4", "V5:PM2"),
                sets("V5:SoNY2/PH3/NBr1/PL4/PM2"));
        assertEquals(Set.of("V5:PM2/PV1"), sets("V5:PM2/PV1"));
    }

    private Set<String> sets(String raw) {
        String result = ReflectionTestUtils.invokeMethod(service, "mapSets", 102368, raw);
        return Set.copyOf(Arrays.asList(result.split(",")));
    }
}
