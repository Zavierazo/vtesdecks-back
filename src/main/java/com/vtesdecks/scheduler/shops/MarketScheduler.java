package com.vtesdecks.scheduler.shops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vtesdecks.cache.SetCache;
import com.vtesdecks.integration.MarketClient;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import com.vtesdecks.model.ShopPlatform;
import com.vtesdecks.model.market.CardOffer;
import com.vtesdecks.model.market.CardOffersResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.isEmpty;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketScheduler {
    private static final Integer LIMIT = 1000;
    private static final Map<String, ShopPlatform> PLATFORM_MAP = Map.of(
            "tcgmarket", ShopPlatform.TCG_MKT,
            "ebay", ShopPlatform.EBAY
    );

    private final CardShopRepository cardShopRepository;
    private final MarketClient marketClient;
    private final SetCache setCache;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "3 0 0 * * MON")
    @Transactional
    public void scrapCards() {
        log.info("Starting Blood Library Market scrapping...");
        int offset = 0;
        try {
            List<CardShopEntity> existingCardSets = cardShopRepository.findByPlatformIn(PLATFORM_MAP.values());
            CardOffersResponse cardOffersResponse = getPage(offset);
            List<CardOffer> offers = new ArrayList<>();
            do {
                if (cardOffersResponse != null && !isEmpty(cardOffersResponse.getOffers())) {
                    offers.addAll(cardOffersResponse.getOffers());
                }
                offset += LIMIT;
                cardOffersResponse = getPage(offset);
            } while (cardOffersResponse != null && cardOffersResponse.getOffers() != null && !cardOffersResponse.getOffers().isEmpty());
            parseOffers(offers, existingCardSets);
            if (!existingCardSets.isEmpty()) {
                log.warn("The following cards are no longer available on Blood Library Market and will be removed: {}", existingCardSets);
                cardShopRepository.deleteAll(existingCardSets);
            }
        } catch (Exception e) {
            log.error("Error scrapping Blood Library Market", e);
        } finally {
            cardShopRepository.flush();
            log.info("Blood Library Market scrap finished!");
        }
    }

    private CardOffersResponse getPage(int offset) {
        return marketClient.getCardOffers(offset, LIMIT);
    }

    private record CompositeKey(ShopPlatform platform, Integer cardId, String set, String locale) {
    }

    private void parseOffers(List<CardOffer> offers, List<CardShopEntity> existingCardSets) {
        List<CardOffer> cheaperOffers = offers.stream()
                .filter(offer -> offer.getPrice() != null && offer.getPrice().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.groupingBy(
                        offer -> new CompositeKey(
                                PLATFORM_MAP.get(offer.getMarket()),
                                offer.getCardId(),
                                offer.getEdition(),
                                offer.getLanguage()
                        ),
                        Collectors.minBy(Comparator.comparing(CardOffer::getPrice))
                ))
                .values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        try {
            for (CardOffer offer : cheaperOffers) {
                CardShopEntity cardShopEntity = toCardShopEntity(offer).orElse(null);
                if (cardShopEntity == null) {
                    continue;
                }
                Optional<CardShopEntity> currentOptional = existingCardSets.stream()
                        .filter(card -> Objects.equals(card.getCardId(), cardShopEntity.getCardId()) &&
                                Objects.equals(card.getPlatform(), cardShopEntity.getPlatform()) &&
                                Objects.equals(card.getSet(), cardShopEntity.getSet()) &&
                                Objects.equals(card.getLocale(), cardShopEntity.getLocale())
                        ).findFirst();
                if (currentOptional.isPresent()) {
                    CardShopEntity currentCard = currentOptional.get();
                    cardShopEntity.setId(currentCard.getId());
                    if (!cardShopEntity.equals(currentCard)) {
                        cardShopRepository.save(cardShopEntity);
                    }
                    existingCardSets.remove(currentCard);
                } else {
                    cardShopRepository.save(cardShopEntity);
                }
            }
            cardShopRepository.flush();
        } catch (Exception e) {
            log.error("Error scrapping Blood Library Market", e);
        }
    }


    private Optional<CardShopEntity> toCardShopEntity(CardOffer cardOffer) {
        if (cardOffer == null || cardOffer.getCardId() == null || cardOffer.getLink() == null || cardOffer.getMarket() == null || cardOffer.getPrice() == null || cardOffer.getCurrency() == null) {
            log.warn("Invalid card offer: {}", cardOffer);
            return Optional.empty();
        }
        ShopPlatform platform = PLATFORM_MAP.get(cardOffer.getMarket());
        if (platform == null) {
            log.debug("No market platform found for {}", cardOffer.getMarket());
            return Optional.empty();
        }
        // Extra validations
        switch (platform) {
            case TCG_MKT:
                if (cardOffer.getEdition() == null) {
                    log.warn("Invalid card offer: {}", cardOffer);
                    return Optional.empty();
                }
                break;
            default:

        }
        String set = null;
        if (cardOffer.getEdition() != null) {
            set = cardOffer.getEdition();
            if (setCache.get(set) == null) {
                log.warn("No set found for {}", set);
                return Optional.empty();
            } else if (cardOffer.getEditionDetails() != null) {
                set += ":" + cardOffer.getEditionDetails();
            }
        }

        // Build data object
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", cardOffer.getId());

        return Optional.of(CardShopEntity.builder()
                .cardId(cardOffer.getCardId())
                .link(cardOffer.getLink())
                .platform(platform)
                .set(set)
                .locale(cardOffer.getLanguage())
                .price(cardOffer.getPrice())
                .currency(cardOffer.getCurrency())
                .inStock(true)
                .data(data)
                .build());
    }


}
