package com.vtesdecks.service;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.DeckCardIndex;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.jpa.repositories.DeckArchetypeRepository;
import com.vtesdecks.util.CosineSimilarityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** The same classification rules for saved decks and unsaved builder contents. */
@Service
@RequiredArgsConstructor
public class DeckArchetypeMatcher {
    private static final double MIN_SIMILARITY = 0.5;

    private final DeckArchetypeRepository repository;
    private final DeckService deckService;
    private final DeckCardIndex deckCardIndex;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;

    public record Reference(DeckArchetypeEntity archetype, DeckSummary deck, Map<Integer, Integer> cards) {}

    /** Load once per scheduled batch, or once for an individual builder request. */
    public List<Reference> loadReferences() {
        List<Reference> references = new ArrayList<>();
        for (DeckArchetypeEntity archetype : repository.findAll()) {
            Stream.of(archetype.getDeckId(), archetype.getSecondaryDeckId())
                    .filter(Objects::nonNull)
                    .distinct()
                    .map(deckService::getSummary)
                    .filter(Objects::nonNull)
                    .forEach(deck -> references.add(new Reference(archetype, deck, deckCardIndex.getCardCounts(deck.getId()))));
        }
        return references;
    }

    public DeckArchetypeEntity findBestMatch(DeckSummary deck, Map<Integer, Integer> cards, List<Reference> references) {
        if (cards.isEmpty()) {
            return null;
        }
        var attributes = ArchetypeAttributeCounter.count(cards, cryptCache, libraryCache);
        DeckArchetypeEntity best = null;
        double bestSimilarity = -1.0;
        for (Reference reference : references) {
            DeckArchetypeEntity archetype = reference.archetype();
            var requirements = archetype.getCardRequirements();
            if (requirements != null && requirements.stream().anyMatch(rule ->
                    cards.getOrDefault(rule.getCardId(), 0) < rule.getMinimumQuantity())) {
                continue;
            }
            var attributeRequirements = archetype.getAttributeRequirements();
            if (attributeRequirements != null && attributeRequirements.stream().anyMatch(rule ->
                    attributes.getOrDefault(new ArchetypeAttributeCounter.Key(rule.getType(), rule.getValue()), 0L) < rule.getMinimumQuantity())) {
                continue;
            }
            double similarity = CosineSimilarityUtils.cosineSimilarity(deck, cards, reference.deck(), reference.cards());
            if (similarity >= MIN_SIMILARITY && (similarity > bestSimilarity
                    || (similarity == bestSimilarity && best != null && archetype.getId() < best.getId()))) {
                best = archetype;
                bestSimilarity = similarity;
            }
        }
        return best;
    }
}
