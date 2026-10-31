package com.vtesdecks.scheduler.shops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vtesdecks.integration.MarketClient;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import com.vtesdecks.model.ShopPlatform;
import com.vtesdecks.model.market.CardOffer;
import com.vtesdecks.model.market.CardOffersResponse;
import com.vtesdecks.service.CurrencyExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketScheduler {
    private static final Integer LIMIT = 1000;
    private static final Map<String, ShopPlatform> PLATFORM_MAP = Map.of(
            "tcgmarket", ShopPlatform.TCG_MKT,
            "ebay", ShopPlatform.EBAY,
            "drivethrucards", ShopPlatform.DTC,
            "gamepod", ShopPlatform.GP
    );

    private final CardShopRepository cardShopRepository;
    private final MarketClient marketClient;
    private final ObjectMapper objectMapper;
    private final CurrencyExchangeService currencyExchangeService;

    @Scheduled(cron = "0 0 0/6 * * *")
    @Transactional
    public void scrapCards() {
        log.info("Starting VTES Market synchronization...");
        try {
            List<CardOffer> offers = getOffers();
            List<CardShopEntity> existingCardSets = new ArrayList<>(cardShopRepository.findByPlatformIn(PLATFORM_MAP.values()));
            parseOffers(offers, existingCardSets);
            if (!existingCardSets.isEmpty()) {
                log.warn("The following cards are no longer available on VTES Market and will be removed: {}", existingCardSets);
                cardShopRepository.deleteAll(existingCardSets);
            }
            cardShopRepository.flush();
            log.info("VTES Market synchronization finished!");
        } catch (RuntimeException e) {
            log.error("Error synchronizing VTES Market", e);
            throw e;
        }
    }

    private List<CardOffer> getOffers() {
        List<CardOffer> offers = new ArrayList<>();
        Set<Integer> offerIds = new HashSet<>();
        Long total = null;
        int offset = 0;
        while (true) {
            CardOffersResponse page = marketClient.getCardOffers(offset, LIMIT);
            if (page == null || page.getTotal() == null || page.getTotal() < 0 || page.getOffers() == null || page.getOffers().size() > LIMIT) {
                throw new IllegalStateException("Invalid VTES Market page at offset " + offset);
            }
            if (total == null) {
                total = page.getTotal();
            } else if (!total.equals(page.getTotal())) {
                throw new IllegalStateException("VTES Market total changed during pagination");
            }
            for (CardOffer offer : page.getOffers()) {
                if (offer == null || offer.getId() == null || !offerIds.add(offer.getId())) {
                    throw new IllegalStateException("Missing or duplicate VTES Market offer ID at offset " + offset);
                }
            }
            offers.addAll(page.getOffers());
            if (offers.size() > total) {
                throw new IllegalStateException("VTES Market returned more offers than its total");
            }
            if (offers.size() == total) {
                return offers;
            }
            if (page.getOffers().size() < LIMIT) {
                throw new IllegalStateException("Incomplete VTES Market response at offset " + offset);
            }
            offset = Math.addExact(offset, LIMIT);
        }
    }

    private record CompositeKey(
            ShopPlatform platform,
            Integer cardId,
            String edition,
            String editionDetails,
            String locale
    ) {
    }

    private void parseOffers(List<CardOffer> offers, List<CardShopEntity> existingCardSets) {
        List<CardOffer> cheaperOffers = offers.stream()
                .filter(this::isImportableOffer)
                .collect(Collectors.groupingBy(
                        offer -> new CompositeKey(
                                PLATFORM_MAP.get(offer.getMarket()),
                                offer.getCardId(),
                                offer.getEdition(),
                                offer.getEditionDetails(),
                                offer.getLanguage()
                        ),
                        Collectors.minBy(Comparator.comparing(CardOffer::getPrice))
                ))
                .values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        for (CardOffer offer : cheaperOffers) {
            CardShopEntity cardShopEntity = toCardShopEntity(offer);
            Optional<CardShopEntity> currentOptional = existingCardSets.stream()
                    .filter(card -> Objects.equals(card.getCardId(), cardShopEntity.getCardId()) &&
                            Objects.equals(card.getPlatform(), cardShopEntity.getPlatform()) &&
                            Objects.equals(card.getSet(), cardShopEntity.getSet()) &&
                            Objects.equals(getMatchingLocale(card), getMatchingLocale(cardShopEntity))
                    )
                    .min(Comparator.comparing(card -> !Objects.equals(card.getLocale(), cardShopEntity.getLocale())));
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
    }

    private String getMatchingLocale(CardShopEntity card) {
        if (card.getLocale() == null && (card.getPlatform() == ShopPlatform.DTC || card.getPlatform() == ShopPlatform.GP)) {
            return "en";
        }
        return card.getLocale();
    }

    private boolean isImportableOffer(CardOffer offer) {
        if (offer.getMarket() == null) {
            throw new IllegalArgumentException("Missing market for VTES Market offer " + offer.getId());
        }
        if (!PLATFORM_MAP.containsKey(offer.getMarket())) {
            log.debug("No market platform found for {}", offer.getMarket());
            return false;
        }
        if (offer.getCardId() == null || offer.getLink() == null || offer.getLink().isBlank() ||
                offer.getPrice() == null || offer.getCurrency() == null || offer.getCurrency().isBlank()) {
            throw new IllegalArgumentException("Invalid VTES Market offer " + offer.getId());
        }
        return offer.getPrice().compareTo(BigDecimal.ZERO) > 0;
    }

    private CardShopEntity toCardShopEntity(CardOffer cardOffer) {
        ShopPlatform platform = PLATFORM_MAP.get(cardOffer.getMarket());
        String set = null;
        if (cardOffer.getEdition() != null) {
            set = cardOffer.getEdition();
            if (cardOffer.getEditionDetails() != null) {
                set += ":" + cardOffer.getEditionDetails();
            }
        }

        // Build data object
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", cardOffer.getId());

        return CardShopEntity.builder()
                .cardId(cardOffer.getCardId())
                .link(cardOffer.getLink())
                .platform(platform)
                .set(set)
                .locale(cardOffer.getLanguage())
                .price(cardOffer.getPrice())
                .currency(cardOffer.getCurrency())
                .priceDefaultCurrency(currencyExchangeService.convert(cardOffer.getPrice(), cardOffer.getCurrency(), DEFAULT_CURRENCY))
                .inStock(true)
                .data(data)
                .build();
    }


}
