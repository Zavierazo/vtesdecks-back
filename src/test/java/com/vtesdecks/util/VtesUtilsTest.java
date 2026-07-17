package com.vtesdecks.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VtesUtilsTest {

    @Test
    public void containsSetMatchesAbbrevWithAndWithoutSubGroup() {
        assertTrue(VtesUtils.containsSet(List.of("Jyhad", "VTES", "Promo:20190601"), "Promo"));
        assertTrue(VtesUtils.containsSet(List.of("Promo"), "Promo"));
        assertTrue(VtesUtils.containsSet(List.of("PFA:1"), "PFA"));
        assertTrue(VtesUtils.containsSet(List.of("Anthology I:LARP"), "Anthology I"));
        assertFalse(VtesUtils.containsSet(List.of("Jyhad", "VTES"), "Promo"));
        assertFalse(VtesUtils.containsSet(List.of(), "Promo"));
        assertFalse(VtesUtils.containsSet(null, "Promo"));
    }

    @Test
    public void addSetAppendsAtTheEndPreservingOrder() {
        assertEquals(List.of("Jyhad", "VTES", "Promo:1"), VtesUtils.addSet(List.of("Jyhad", "VTES"), "Promo:1"));
        assertEquals(List.of("Promo:1"), VtesUtils.addSet(List.of(), "Promo:1"));
        assertEquals(List.of("Promo:1"), VtesUtils.addSet(null, "Promo:1"));
    }

    @Test
    public void addSetDoesNothingWhenAbbrevAlreadyPresent() {
        assertEquals(List.of("Jyhad", "Promo:20190601"), VtesUtils.addSet(List.of("Jyhad", "Promo:20190601"), "Promo:1"));
        assertEquals(List.of("Promo"), VtesUtils.addSet(List.of("Promo"), "Promo:1"));
    }

    @Test
    public void removeSetRemovesEveryTokenWithTheAbbrev() {
        assertEquals(List.of("Jyhad", "PFA:1"), VtesUtils.removeSet(List.of("Jyhad", "Promo:20190601", "PFA:1"), "Promo"));
        assertEquals(List.of("PFA:1"), VtesUtils.removeSet(List.of("Promo:20190601", "Promo:20200101", "PFA:1"), "Promo"));
        assertEquals(List.of("PFA:1"), VtesUtils.removeSet(List.of("Promo", "PFA:1"), "Promo"));
        assertEquals(List.of("Jyhad", "VTES"), VtesUtils.removeSet(List.of("Jyhad", "VTES"), "Promo"));
    }

    @Test
    public void promoRuleKeepsPromoWhenPromoImageExists() {
        assertEquals(List.of("Jyhad", "Promo:20190601"),
                VtesUtils.applyPromoImageRule(List.of("Jyhad", "Promo:20190601"), true, false));
        assertEquals(List.of("Jyhad", "Promo:20190601", "PFA:1"),
                VtesUtils.applyPromoImageRule(List.of("Jyhad", "Promo:20190601", "PFA:1"), true, true));
    }

    @Test
    public void promoRuleRemovesPromoWhenOnlyPfaImageExists() {
        assertEquals(List.of("Jyhad", "PFA:1"),
                VtesUtils.applyPromoImageRule(List.of("Jyhad", "Promo:20190601", "PFA:1"), false, true));
    }

    @Test
    public void promoRuleKeepsPromoWhenNoImageExists() {
        assertEquals(List.of("Jyhad", "Promo:20190601"),
                VtesUtils.applyPromoImageRule(List.of("Jyhad", "Promo:20190601"), false, false));
    }

    @Test
    public void promoRuleAddsPromoWhenPromoImageExists() {
        assertEquals(List.of("Jyhad", "VTES", "Promo:1"),
                VtesUtils.applyPromoImageRule(List.of("Jyhad", "VTES"), true, false));
        assertEquals(List.of("PFA:1", "Promo:1"),
                VtesUtils.applyPromoImageRule(List.of("PFA:1"), true, true));
    }

    @Test
    public void promoRuleDoesNothingWithoutPromoNorImages() {
        assertEquals(List.of("Jyhad", "VTES"),
                VtesUtils.applyPromoImageRule(List.of("Jyhad", "VTES"), false, false));
        assertEquals(List.of("PFA:1"),
                VtesUtils.applyPromoImageRule(List.of("PFA:1"), false, true));
    }
}
