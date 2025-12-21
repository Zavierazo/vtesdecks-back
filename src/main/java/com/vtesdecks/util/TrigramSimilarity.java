package com.vtesdecks.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility class to compute trigram similarity between two strings.
 * <p>
 * Implementation based on provided TypeScript version. It:
 * - normalizes input (trim, collapse spaces, replace single spaces with double spaces, add padding)
 * - generates trigrams (3-grams)
 * - filters out trivial trigrams
 * - computes Jaccard-like similarity = intersection / union
 */
@UtilityClass
public final class TrigramSimilarity {
    private static final Pattern TRIVIAL_TRIGRAM = Pattern.compile("^[\\p{L}\\p{M}\\p{N}]\\s\\s$", Pattern.UNICODE_CHARACTER_CLASS);


    private static Set<String> nGram(String value, int nGram) {
        Set<String> ngrams = new HashSet<>();
        if (value == null || value.length() < nGram) {
            return ngrams;
        }
        for (int i = 0; i <= value.length() - nGram; i++) {
            ngrams.add(value.substring(i, i + nGram));
        }
        return ngrams;
    }

    private static String normalizeString(String input) {
        if (input == null || input.trim().isEmpty()) {
            return StringUtils.EMPTY;
        }
        return input.trim().replaceAll("\\s+", " ").toLowerCase();
    }


    public static Set<String> generateTrigram(String input) {
        String normalizedInput = normalizeString(input);
        Set<String> grams = nGram(normalizedInput, 3);
        return grams.stream()
                .filter(g -> !TRIVIAL_TRIGRAM.matcher(g).matches())
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * Calculate trigram similarity between two strings. Returns a value between 0 and 1.
     * <p>
     * Behavior matches the TypeScript implementation:
     * - exact equality => 1
     * - if input1 contains input2 (case-insensitive) => 1
     * - otherwise: intersection/union of trigrams
     */
    public static BigDecimal trigramSimilarity(String input1, String input2, Set<String> t1, Set<String> t2) {
        if (input1 != null && input1.equals(input2)) {
            return BigDecimal.ONE;
        }
        if (input1 != null && input2 != null && input1.toLowerCase().contains(input2.toLowerCase())) {
            return BigDecimal.ONE;
        }

        if (t1.isEmpty() && t2.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Set<String> union = new HashSet<>(t1);
        union.addAll(t2);

        Set<String> intersection = new HashSet<>(t1);
        intersection.retainAll(t2);

        if (union.isEmpty()) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal numerator = BigDecimal.valueOf(intersection.size());
        BigDecimal denominator = BigDecimal.valueOf(union.size());
        return numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
