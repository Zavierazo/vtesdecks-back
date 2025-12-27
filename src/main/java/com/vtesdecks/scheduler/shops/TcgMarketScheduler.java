package com.vtesdecks.scheduler.shops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.SetCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.integration.TcgMarketClient;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.entity.extra.TextSearch;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.model.ShopPlatform;
import com.vtesdecks.model.tcgmarket.MarketResponse;
import com.vtesdecks.model.tcgmarket.MarketResult;
import com.vtesdecks.util.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.Map.entry;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

@Slf4j
@Component
@RequiredArgsConstructor
public class TcgMarketScheduler {
    private static final ShopPlatform PLATFORM = ShopPlatform.TCG_MKT;
    private static final String EURO = "EUR";
    private static final String SPECIAL_CHAR_REGEX = "[_,:\"'”\\s]";
    private static final List<Map.Entry<String, String>> REPLACEMENTS = List.of(
            // Replacement for vampires with same name
            entry("Gilbert Duane [2021]", "Gilbert Duane (G6)"),
            entry("Victoria Ash [2021]", "Victoria Ash (G7)"),
            entry("Mithras [2019]", "Mithras (G6)"),
            entry("Tegyrius, Vizier [2024]", "Tegyrius, Vizier (G6)"),
            // General cleanup
            entry(" \\[.*?\\]", ""),
            entry(" \\((?!G\\d+|Adv\\))[^)]*\\)", ""),
            // Typos
            entry("Sebastian Goulet", " Sébastien Goulet"),
//            entry("Raven", "Camille Devereux, The Raven"),
            entry("Hiram Hide DrVries", "Hiram \"Hide\" DeVries"),
            entry("Fidai", "Fida'i"),
            entry("LEpuisette", "L'Epuisette"),
            entry("Bears Skin", "Bear's Skin"),
            entry("Spirits Touch", "Spirit's Touch"),
            entry("Crocodiles Tongue", "Crocodile's Tongue"),
            entry("Brothers Blood", "Brother's Blood"),
            entry("Geminis Mirror", "Gemini's Mirror"),
            entry("Scorpions Touch", "Scorpion's Touch"),
            entry("Sets Call", "Set's Call"),
            entry("Giants Blood", "Giant's Blood"),
            entry("Sacre-Cour Cathedral, France", "Sacré-Cœur Cathedral, France"),
            entry("Thadius Zho, Mage", "Thadius Zho"),
            entry("Vozhd of Sofia", "Vozhd of Sofia, The")
    );
    private static final Map<String, String> EDITION_MAPPINGS = Map.ofEntries(
            entry("Anth", "Anthology"),
            entry("Anth1", "Anthology I"),
            entry("SabbatV5", "SV5"),
            entry("Fifth-A", "V5A"),
            entry("Fifth", "V5"),
            entry("V5Lasombra", "V5L"),
            entry("V5Hecata", "V5H"),
            entry("HttBrp", "POD"),
            entry("PrecoS", "SP"),
            entry("KoTrp", "POD"),
            entry("HumbleBundle", "Promo")
    );

    private static final Map<String, String> LANGUAGE_MAPPING = Map.ofEntries(
            entry("Español", "es"),
            entry("Francés", "fr"),
            entry("Inglés", "en"),
            entry("Portugués", "en")
    );


    private final DeckCardRepository deckCardRepository;
    private final CardShopRepository cardShopRepository;
    private final TcgMarketClient tcgMarketClient;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;
    private final SetCache setCache;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "4 0 0 * * MON")
//    @Scheduled(initialDelay = 0, fixedDelay = Long.MAX_VALUE)
    @Transactional
    public void scrapCards() {
        log.info("Starting TcgMarket scrapping...");
        int page = 1;
        try {
            List<CardShopEntity> existingCardSets = cardShopRepository.findByPlatform(PLATFORM);
            Set<CardShopEntity> newCards = new HashSet<>();
            MarketResponse results = getPage(page);
            do {
                if (results != null && !isEmpty(results.getResults())) {
                    parsePage(results.getResults(), newCards, existingCardSets);
                }
                page++;
                results = getPage(page);
            } while (results != null && results.getNext() != null && !results.getResults().isEmpty());
            if (!existingCardSets.isEmpty()) {
                log.warn("The following cards are no longer available on TcgMarket and will be removed from stock: {}", existingCardSets);
                for (CardShopEntity cardToRemove : existingCardSets) {
                    if (cardToRemove != null && cardToRemove.isInStock()) {
                        log.warn("The card has been removed from stock: {}", cardToRemove);
                        cardToRemove.setInStock(false);
                        cardShopRepository.save(cardToRemove);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error scrapping TcgMarket page " + page, e);
        } finally {
            cardShopRepository.flush();
            log.info("TcgMarket scrap finished!");
        }
    }

    private MarketResponse getPage(int page) {
        return tcgMarketClient.getProducts(page, 200);
    }

    private void parsePage(List<MarketResult> results, Set<CardShopEntity> newCards, List<CardShopEntity> existingCardSets) {
        for (MarketResult result : results) {
            CardShopEntity cardShopEntity = scrapCard(result).orElse(null);
            if (cardShopEntity == null) {
                continue;
            }
            List<CardShopEntity> existingCards = cardShopRepository.findByCardIdAndPlatform(cardShopEntity.getCardId(), PLATFORM);
            Optional<CardShopEntity> currentOptional = existingCards.stream()
                    .filter(card -> Objects.equals(card.getSet(), cardShopEntity.getSet()) && Objects.equals(card.getLocale(), cardShopEntity.getLocale()))
                    .findFirst();
            if (currentOptional.isPresent()) {
                CardShopEntity currentCard = currentOptional.get();
                cardShopEntity.setId(currentCard.getId());
                if (!cardShopEntity.equals(currentCard)) {
                    cardShopRepository.save(cardShopEntity);
                }
            } else {
                Optional<CardShopEntity> duplicateInNew = newCards.stream()
                        .filter(card ->
                                Objects.equals(card.getCardId(), cardShopEntity.getCardId()) &&
                                        Objects.equals(card.getSet(), cardShopEntity.getSet()) &&
                                        Objects.equals(card.getLocale(), cardShopEntity.getLocale())
                        ).findFirst();
                if (duplicateInNew.isPresent()) {
                    log.warn("Duplicated card detected in the same scrapping session: {} vs {}", cardShopEntity, duplicateInNew.get());
                } else {
                    cardShopRepository.save(cardShopEntity);
                    newCards.add(cardShopEntity);
                }
            }
            existingCardSets.removeIf(card ->
                    Objects.equals(card.getCardId(), cardShopEntity.getCardId()) &&
                            Objects.equals(card.getSet(), cardShopEntity.getSet()) &&
                            Objects.equals(card.getLocale(), cardShopEntity.getLocale())
            );
        }
        cardShopRepository.flush();
    }

    private Optional<CardShopEntity> scrapCard(MarketResult marketResult) {
        if (marketResult.getMinPrice() == null) {
            return Optional.empty();
        }
        if (marketResult.getVendors() == null || marketResult.getVendors() <= 0) {
            return Optional.empty();
        }
        if (marketResult.getEdition() != null) {
            String setName = marketResult.getEdition().getName();
            if ("Storyline".equalsIgnoreCase(setName) || "Demo".equalsIgnoreCase(setName)) {
                return Optional.empty();
            }
        }


        // Card URL
        String url = "https://tcgmarket.es/details/" + marketResult.getId();

        // Prepare card name
        String cardNameRaw = marketResult.getCardName();
        String cardName = cardNameRaw;
        // Promo
        boolean fullArt = cardName.toLowerCase().contains("[full art]");
        // Replacements
        for (Map.Entry<String, String> entry : REPLACEMENTS) {
            cardName = cardName.replaceFirst("(?i)" + entry.getKey(), entry.getValue());
        }
        if (cardName.equalsIgnoreCase("Raven")) {
            cardName = "Camille Devereux, The Raven";
        }
        // Advanced handling
        int advancedIndex = cardName.toLowerCase().indexOf("(adv)");
        boolean advanced = advancedIndex > 0;
        if (advanced) {
            cardName = cardName.substring(0, advancedIndex);
        } else if (cardName.toLowerCase().endsWith(" adv")) {
            int advancedAltIndex = cardName.toLowerCase().indexOf(" adv");
            advanced = advancedAltIndex > 0;
            if (advanced) {
                cardName = cardName.substring(0, advancedAltIndex);
            }
        }
        // The handling
        if (cardName.startsWith("The ")) {
            cardName = cardName.substring(4) + ", The";
        }
        // An handling
        if (cardName.startsWith("An ")) {
            cardName = cardName.substring(4) + ", An";
        }
        // Trim
        cardName = cardName.trim();

        // Set
        com.vtesdecks.cache.indexable.Set set = null;
        if (fullArt) {
            set = setCache.get("PFA");
        } else if (marketResult.getEdition() != null && marketResult.getEdition().getName() != null) {
            String setName = marketResult.getEdition().getName();
            if (EDITION_MAPPINGS.containsKey(setName)) {
                setName = EDITION_MAPPINGS.get(setName);
            }
            set = setCache.get(setName);
            if (set == null) {
                set = setCache.getByFullName(setName);
            }
            if (set == null && !setName.equals("DEMO")) {
                log.warn("Unknown set '{}' for card '{}' with url {}", setName, cardNameRaw, url);
            }
        }

        // Locale
        String locale = null;
        if (marketResult.getLanguage() != null && marketResult.getLanguage().getName() != null) {
            locale = LANGUAGE_MAPPING.get(marketResult.getLanguage().getName());
        }
        if (locale == null) {
            log.warn("Unknown language '{}' with url {}", marketResult.getLanguage(), url);
            locale = "en";
        }

        // Price info
        BigDecimal price = marketResult.getMinPrice();

        // Find card id
        Integer cardId = null;

        try (ResultSet<Crypt> result = cryptCache.selectByExactName(cardName)) {
            if (!result.isEmpty() && result.size() == 1) {
                cardId = result.stream().map(Crypt::getId).findFirst().orElse(null);
            }
        }
        try (ResultSet<Library> result = libraryCache.selectByExactName(cardName)) {
            if (!result.isEmpty() && result.size() == 1) {
                cardId = result.stream().map(Library::getId).findFirst().orElse(null);
            }
        }
        if (cardId == null) {
            List<TextSearch> cards = deckCardRepository.search(cardName, advanced);
            if (CollectionUtils.isEmpty(cards)) {
                log.warn("Unable to found card with name '{}' with url {}", cardNameRaw, url);
                return Optional.empty();
            } else if (cards.size() > 1) {
                final String cardNameNormalized = Utils.normalizeName(cardName).replaceAll(SPECIAL_CHAR_REGEX, "");
                final String cardNameFinal = cardName;
                Optional<TextSearch> exactCard = cards.stream()
                        .filter(cardSearch ->
                                cardSearch.getName().equalsIgnoreCase(cardNameFinal) ||
                                        Utils.normalizeName(cardSearch.getName()).replaceAll(SPECIAL_CHAR_REGEX, "").equalsIgnoreCase(cardNameNormalized))
                        .findFirst();
                if (exactCard.isPresent()) {
                    cardId = exactCard.get().getId();
                } else {
                    log.warn("Multiple finds for '{}' with raw '{}': {} with url {}", cardName, cardNameRaw, cards.stream().map(TextSearch::getName).toList(), url);
                    return Optional.empty();
                }
            } else {
                cardId = cards.getFirst().getId();
            }
        }
        if (cardId == null) {
            log.warn("Unable to found card with name '{}' with raw '{}' with url {}", cardName, cardNameRaw, url);
            return Optional.empty();
        }


        // Build data object
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", marketResult.getId());
        data.put("product_id", marketResult.getProductId());

        // Build entity
        return Optional.of(CardShopEntity.builder()
                .cardId(cardId)
                .link(url)
                .platform(PLATFORM)
                .set(set != null ? set.getAbbrev() : null)
                .locale(locale)
                .price(price)
                .currency(EURO)
                .inStock(true)
                .data(data)
                .build());
    }


}
