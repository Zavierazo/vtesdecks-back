package com.vtesdecks.util;

import com.vtesdecks.cache.indexable.Deck;
import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.Map;

@UtilityClass
public class CosineSimilarityUtils {


    public static double cosineSimilarity(Deck deckA, Map<Integer, Integer> vecA, Deck deckB, Map<Integer, Integer> vecB) {
        double dot = 0;
        for (Map.Entry<Integer, Integer> entry : vecA.entrySet()) {
            int id = entry.getKey();
            if (vecB.containsKey(id)) {
                dot += entry.getValue() * vecB.get(id);
            }
        }

        // Norms
        double normA = deckA.getL2Norm();
        double normB = deckB.getL2Norm();

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dot / (normA * normB);
    }

    public static Map<Integer, Integer> getVector(Deck deck) {
        Map<Integer, Integer> vector = new HashMap<>();
        if (deck == null) {
            return vector;
        }
        if (deck.getCrypt() != null) {
            deck.getCrypt().forEach(card -> vector.put(card.getId(), card.getNumber()));
        }
        if (deck.getLibraryByType() != null) {
            deck.getLibraryByType().values().forEach(cards -> cards.forEach(card -> vector.put(card.getId(), card.getNumber())));
        }
        return vector;
    }

    public static double computeL2Norm(Map<Integer, Integer> vector) {
        return Math.sqrt(
                vector.values().stream()
                        .mapToDouble(count -> count * count)
                        .sum()
        );
    }


}
