package com.vtesdecks.scheduler.shops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.SetCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.indexable.Set;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.entity.extra.TextSearch;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.model.ShopPlatform;
import com.vtesdecks.util.Utils;
import com.vtesdecks.util.VtesUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.typesense.api.Client;
import org.typesense.model.SearchParameters;
import org.typesense.model.SearchResult;
import org.typesense.model.SearchResultHit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Map.entry;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardGameGeekScheduler {
    private static final ShopPlatform PLATFORM = ShopPlatform.CGG;
    private static final String EURO = "EUR";
    private static final String COLLECTION = "20250207_products";
    private static final String PRESET = "xavier_vtes";
    private static final String SPECIAL_CHAR_REGEX = "[_,:\"'”\\s]";
    private static final String MULTIPLE_REPLACEMENT_REGEX = "(\\(G\\d+\\))(?:\\s*\\(G\\d+\\))+";
    private static final List<Map.Entry<String, String>> REPLACEMENTS = List.of(
            // Replacement for vampires with same name
            entry("Gilbert Duane G6", "Gilbert Duane (G6)"),
            entry("Hesha Ruhadze G6", "Hesha Ruhadze (G6)"),
            entry("Kalinda - 5th", "Kalinda (G6)"),
            entry("Theo Bell G6", "Theo Bell (G6)"),
            entry("Victoria Ash - 5th Edition", "Victoria Ash (G7)"),
            entry("Evan Klein - New Blood", "Evan Klein (G6)"),
            entry("Mithras G6", "Mithras (G6)"),
            entry("Tegyrius, Vizier - Promo", "Tegyrius, Vizier (G6)"),
            entry("Francois Villon G6", "François Villon (G6)"),
            entry("Annabelle Triabell - 30th Anniversary", "Annabelle Triabell (G6)"),
            entry("Queen Anne - Fallen London", "Queen Anne (G6)"),
            entry("Nikolaus Vermeulen - 30th Anniversary", "Nikolaus Vermeulen (G6)"),
            entry("Lucinde, Alastor - 30th Anniversary", "Lucinde, Alastor (G7)"),
            entry("Donal O'Connor - 30th Anniversary", "Dónal O'Connor (G6)"),
            // Remove group marks
            entry(" G1", ""),
            entry(" G3", ""),
            entry(" G2", ""),
            entry(" G6", ""),
            entry(" \\((?!G\\d+|Adv\\))[^)]*\\)", ""),
            // Map aka names
            entry("Sebastian Goulet", "Sebastien Goulet"),
            // Fix name
            entry("Enzo Giovanni \\(Adv\\)", "Enzo Giovanni, Pentex Board of Directors (Adv)"),
            entry("47th Street Royal", "47th Street Royals"),
            entry("Crimson Sentinel", "Crimson Sentinel, The"),
            entry("Pentex Subversion", "Pentex(TM) Subversion"),
            entry("Khabar: The Community", "Khabar: Community, The"),
            entry("Sacre Cour", "Sacré-Cœur"),
            entry("Thadius Zho, Mage", "Thadius Zho"),
            entry("Vozhd of Sofia", "Vozhd of Sofia, The"),
            entry("Seeds of Fear", "Seeds of Terror")
    );
    private static final Map<String, String> EDITION_MAPPINGS = Map.ofEntries(
            entry("BCP Reprint", "POD"),
            entry("BCP POD", "POD"),
            entry("BCP First Blood", "FB"),
            entry("BCP New Blood", "NB"),
            entry("BCP Anthology I", "Anthology"),
            entry("Blood Shadow Court", "BSC"),
            entry("Berlin Anthology", "Anthology"),
            entry("Legacy of Blood", "LoB"),
            entry("10th Anniversary", "Tenth"),
            entry("BCP 25th Anniversary", "25th"),
            entry("30th Anniversary", "30th"),
            entry("5th Edition", "V5"),
            entry("5th - Hecata", "V5H"),
            entry("Promo - Full Bleed", "PFA"),
            entry("Promo - Full Bleed - Portuguese", "PFA"),
            entry("Anarchs and Alastor Storyline", "Anarchs"),
            entry("Anarchs Unbound", "AU"),
            entry("Humble Bundle", "Promo"),
            entry("Julius Stonis Vilnius", "Promo"),
            entry("Fallen London", "FoL"),
            entry("V5 Sabbat", "SV5"),
            entry("Promo - Full Bleed - Catalan", "PFA"),
            entry("Promo - Full Bleed - Latin", "PFA"),
            entry("Promo - Full Bleed - French", "PFA"),
            entry("Promo - Latin", "Promo"),
            entry("Promo - French", "Promo")
    );

    private final DeckCardRepository deckCardRepository;
    private final CardShopRepository cardShopRepository;
    private final SetCache setCache;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;
    private final Client client;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "2 0 0 * * MON")
    @Transactional
    public void scrapCards() {
        log.info("Starting CardGameGeek scrapping...");
        int page = 1;
        try {
            java.util.Set<Pair<Integer, String>> existingCardSets = cardShopRepository.findByPlatform(PLATFORM).stream()
                    .map(cardShopEntity -> Pair.of(cardShopEntity.getCardId(), cardShopEntity.getSet()))
                    .collect(Collectors.toSet());
            SearchResult searchResult = getPage(page);
            do {
                if (searchResult != null) {
                    parsePage(searchResult.getHits(), existingCardSets);
                }
                page++;
                searchResult = getPage(page);
            } while (searchResult != null && !searchResult.getHits().isEmpty());
            if (!existingCardSets.isEmpty()) {
                log.warn("The following cards are no longer available on CardGameGeek and will be removed from stock: {}", existingCardSets);
                for (Pair<Integer, String> cardSet : existingCardSets) {
                    CardShopEntity cardsToRemove = cardShopRepository.findByCardIdAndPlatformAndSet(cardSet.getLeft(), PLATFORM, cardSet.getRight());
                    if (cardsToRemove != null && cardsToRemove.isInStock()) {
                        log.warn("The card has been removed from stock: {}", cardsToRemove);
                        cardsToRemove.setInStock(false);
                        cardShopRepository.save(cardsToRemove);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error scrapping CardGameGeek page " + page, e);
        } finally {
            cardShopRepository.flush();
            log.info("CardGameGeek scrap finished!");
        }
    }

    private SearchResult getPage(int page) throws Exception {
        SearchParameters searchParameters = new SearchParameters()
                .preset(PRESET)
                .page(page)
                .sortBy("name:asc")
                .perPage(250);
        return client.collections(COLLECTION).documents().search(searchParameters);
    }

    private void parsePage(List<SearchResultHit> hits, java.util.Set<Pair<Integer, String>> existingCardSets) {
        for (SearchResultHit hit : hits) {
            Map<String, Object> document = hit.getDocument();

            // Scrap main card
            CardShopEntity baseCardShopEntity = scrapCard(document, null, false).orElse(null);
            if (baseCardShopEntity != null) {
                List<CardShopEntity> cardShopEntityList = new ArrayList<>();
                cardShopEntityList.add(baseCardShopEntity);
                // Scrap variants
                if (document.containsKey("hasVariants")) {
                    for (Map<String, Object> variant : (List<Map<String, Object>>) document.get("hasVariants")) {
                        scrapCard(variant, baseCardShopEntity.getLink(), true).ifPresent(cardShopEntityList::add);
                    }
                    // Set in stock if any variant is in stock
                    baseCardShopEntity.setInStock(cardShopEntityList.stream().anyMatch(CardShopEntity::isInStock));
                }
                // Save all found cards
                if (!isEmpty(cardShopEntityList)) {
                    Map<String, CardShopEntity> cardShopBySet = cardShopEntityList.stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    c -> c.getCardId() + "_" + c.getSet(),
                                    c -> c,
                                    (existing, replacement) -> {
                                        log.debug("Duplicate CardShop entry for cardId {} set {}: existing [{}], replacement [{}]",
                                                existing.getCardId(), existing.getSet(),
                                                existing.getLink(), replacement.getLink());
                                        if (existing.isInStock() && !replacement.isInStock()) {
                                            return existing;
                                        } else if (!existing.isInStock() && replacement.isInStock()) {
                                            return replacement;
                                        } else {
                                            return existing.getPrice().compareTo(replacement.getPrice()) <= 0 ? existing : replacement;
                                        }
                                    }
                            ));
                    List<CardShopEntity> existingCards = cardShopRepository.findByCardIdAndPlatform(baseCardShopEntity.getCardId(), PLATFORM);
                    for (CardShopEntity cardShopEntity : cardShopBySet.values()) {
                        Optional<CardShopEntity> currentOptional = existingCards.stream()
                                .filter(card -> Objects.equals(card.getCardId(), cardShopEntity.getCardId()) && Objects.equals(card.getSet(), cardShopEntity.getSet()))
                                .findFirst();
                        if (currentOptional.isPresent()) {
                            CardShopEntity currentCard = currentOptional.get();
                            cardShopEntity.setId(currentCard.getId());
                            if (!cardShopEntity.equals(currentCard)) {
                                cardShopRepository.saveAndFlush(cardShopEntity);
                            }
                        } else {
                            cardShopRepository.saveAndFlush(cardShopEntity);
                        }
                        existingCardSets.removeIf(cardSet -> Objects.equals(cardSet.getLeft(), cardShopEntity.getCardId()) && Objects.equals(cardSet.getRight(), cardShopEntity.getSet()));
                    }
                }
            }
        }
    }

    private Optional<CardShopEntity> scrapCard(Map<String, Object> document, String overrideUrl, boolean mandatorySet) {
        Map<String, Object> searchInfo = (Map<String, Object>) document.get("@search");
        if (document.get("additionalProperty") instanceof List) {
            for (Map<String, Object> property : (List<Map<String, Object>>) document.get("additionalProperty")) {
                if ("Edition".equals(property.get("name"))) {
                    String setName = String.valueOf(property.get("value"));
                    if (setName.equalsIgnoreCase("Demo Deck")) {
                        log.debug("Skipping not legal for play product {}", document.get("name"));
                        return Optional.empty();
                    }
                }
                if ("Set".equals(property.get("name"))) {
                    String setName = String.valueOf(property.get("value"));
                    if (setName.equalsIgnoreCase("Storyline")) {
                        log.debug("Skipping not legal for play product {}", document.get("name"));
                        return Optional.empty();
                    }
                }
                if ("Type".equals(property.get("name"))) {
                    String typeValue = String.valueOf(property.get("value"));
                    if (typeValue.equalsIgnoreCase("Special")) {
                        log.debug("Skipping special product {}", document.get("name"));
                        return Optional.empty();
                    }
                }
            }
        }

        // Product info
        String url = String.valueOf(document.get("@id"));
        String sku = String.valueOf(document.get("sku"));
        if (sku != null && sku.startsWith("PLA-")) {
            log.debug("Skipping platform product {}", document.get("name"));
            return Optional.empty();
        }

        // Prepare card name
        String cardNameRaw = String.valueOf(document.get("name"));
        if (cardNameRaw.startsWith("Vampire The Eternal Struggle - ")) {
            log.debug("Skipping bundle/product pack '{}'", cardNameRaw);
            return Optional.empty();
        }
        String cardName = cardNameRaw;
        // Replacements
        for (Map.Entry<String, String> entry : REPLACEMENTS) {
            cardName = cardName.replaceFirst("(?i)" + entry.getKey(), entry.getValue());
        }
        // Advanced handling
        int advancedIndex = cardName.toLowerCase().indexOf("(adv)");
        boolean advanced = advancedIndex > 0;
        if (advanced) {
            cardName = cardName.substring(0, advancedIndex);
        }
        // Promo handling
        int promoIndex = cardName.toLowerCase().indexOf("- promo");
        if (promoIndex == -1) {
            promoIndex = cardName.toLowerCase().indexOf("- bcp promo");
        }
        boolean promo = promoIndex > 0;
        boolean fullArt = cardName.toLowerCase().contains("- full bleed");
        if (promo) {
            cardName = cardName.substring(0, promoIndex);
        }
        // Clear duplicated (G7), (G6), etc.
        cardName = cardName.replaceAll(MULTIPLE_REPLACEMENT_REGEX, "$1").trim();
        // Remove any extra info
        int extraInfoIndex = cardName.indexOf(" -");
        if (extraInfoIndex > 0) {
            cardName = cardName.substring(0, extraInfoIndex);
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

        // Find card by name
        Integer cardId = null;
        final String cardNameNormalized = Utils.normalizeName(cardName).replaceAll(SPECIAL_CHAR_REGEX, "");
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
            log.warn("Unable to found card with name '{}' with url {}", cardNameRaw, url);
            return Optional.empty();
        }


        // Price info

        boolean inStock = Boolean.valueOf(String.valueOf(searchInfo.get("InStock_facet")));
        BigDecimal price = new BigDecimal(String.valueOf(searchInfo.get("Price")));


        // Set
        Set set = null;
        if (document.get("additionalProperty") instanceof List) {
            for (Map<String, Object> property : (List<Map<String, Object>>) document.get("additionalProperty")) {
                if ("Edition".equals(property.get("name"))) {
                    String setName = String.valueOf(property.get("value"));
                    if (EDITION_MAPPINGS.containsKey(setName)) {
                        setName = EDITION_MAPPINGS.get(setName);
                    }
                    set = setCache.get(setName);
                    if (set == null) {
                        set = setCache.getByFullName(setName);
                    }
                    if (set == null) {
                        log.warn("Unknown set '{}' for card '{}' with url {}", setName, cardNameRaw, url);
                        return Optional.empty();
                    }
                    break;
                }
            }
        }
        if (set == null && promo) {
            if (fullArt) {
                set = setCache.get("PFA");
            } else {
                set = setCache.get("Promo");
            }
        } else if (set == null && !document.containsKey("hasVariants")) {
            if (VtesUtils.isCrypt(cardId)) {
                Crypt crypt = cryptCache.get(cardId);
                set = findUniqueSet(crypt.getSets());
            } else {
                Library library = libraryCache.get(cardId);
                set = findUniqueSet(library.getSets());
            }
            if (set == null) {
                log.warn("Unable to found set for card '{}' with url {}", cardNameRaw, url);
                return Optional.empty();
            }
        }
        if (set == null && mandatorySet) {
            log.warn("Set is mandatory but not found for card '{}' with url {}", cardNameRaw, url);
            return Optional.empty();
        }

        // Build data object
        ObjectNode data = objectMapper.createObjectNode();
        data.put("sku", sku);

        // Build entity
        return Optional.of(CardShopEntity.builder()
                .cardId(cardId)
                .link(overrideUrl != null ? overrideUrl : url)
                .platform(PLATFORM)
                .set(set != null ? set.getAbbrev() : null)
                .locale(null)
                .price(price)
                .currency(EURO)
                .inStock(inStock)
                .data(data)
                .build());
    }

    private Set findUniqueSet(List<String> sets) {
        if (sets.size() == 1) {
            return getSet(sets);
        }
        List<String> setWithoutPromo = sets.stream()
                .filter(s -> !s.equalsIgnoreCase("Promo") && !s.equalsIgnoreCase("POD"))
                .toList();
        if (setWithoutPromo.isEmpty()) {
            return getSet(sets);
        }
        return getSet(setWithoutPromo);
    }

    private Set getSet(List<String> sets) {
        String[] setSplit = sets.getFirst().split(":");
        return setCache.get(setSplit[0]);
    }


}
