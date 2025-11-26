package com.vtesdecks.scheduler;

import com.vtesdecks.integration.DTCClient;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.entity.extra.TextSearch;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.model.ShopPlatform;
import com.vtesdecks.model.dtc.Product;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Slf4j
@Component
public class DriveThruCardsScheduler {
    private static final ShopPlatform PLATFORM = ShopPlatform.DTC;
    private static final String SET = "POD:DTC";
    private static final String DOLLAR = "USD";
    private static final String SPECIAL_CHARACTERS = "[_,:\"'”\\s]";
    private static final Pattern PRODUCT_ID_REGEX = Pattern.compile(".*\\/product\\/(?<productId>\\d+)\\/.*");
    public static final int GROUP_ID = 26;
    public static final int SITE_ID = 73;
    public static final int BLACK_CHANTRY_CATEGORY_ID = 34260;

    @Autowired
    private DTCClient dtcClient;
    @Autowired
    private DeckCardRepository deckCardRepository;
    @Autowired
    private CardShopRepository cardShopRepository;

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void scrapCards() {
        log.info("Starting DTC scrapping...");
        List<CardShopEntity> currentCards = cardShopRepository.findByPlatform(PLATFORM);
        int pageIndex = 1;
        List<Product> page = null;
        do {
            try {
                page = getProducts(pageIndex);
                if (page != null) {
                    parsePage(page, currentCards);
                }
                pageIndex++;
            } catch (Exception e) {
                log.error("Error scrapping DTC page {}", pageIndex, e);
            }
        } while (page != null && !page.isEmpty());
        if (!isEmpty(currentCards.size())) {
            cleanOutdatedCards(currentCards);
        }
        log.info("DTC scrap finished!");
    }

    private void cleanOutdatedCards(List<CardShopEntity> currentCards) {
        for (CardShopEntity cardShop : currentCards) {
            try {
                Matcher matcher = PRODUCT_ID_REGEX.matcher(cardShop.getLink());
                if (matcher.matches()) {
                    Integer productId = Integer.parseInt(matcher.group("productId"));
                    Product product = getProduct(productId);
                    if (product == null) {
                        log.warn("Card {} no longer exists in shop {}", cardShop.getCardId(), cardShop.getLink());
                        cardShopRepository.deleteById(cardShop.getId());
                    } else {
                        log.debug("Card {} still exists in shop {}", cardShop.getCardId(), cardShop.getLink());
                    }
                } else {
                    log.warn("Card {} unknown productId", cardShop.getCardId());
                }
            } catch (Exception e) {
                log.error("Error scrapping DTC product {}", cardShop.getLink(), e);
            }
        }
        cardShopRepository.flush();
    }


    private void parsePage(List<Product> page, List<CardShopEntity> currentCards) {
        for (Product productCard : page) {
            try {
                Integer cardId = scrapCard(productCard);
                if (cardId != null) {
                    currentCards.removeIf(cardShop -> cardShop.getCardId().equals(cardId));
                }
            } catch (Exception e) {
                log.error("Error scrapping DTC element {}", productCard, e);
            }
        }
        cardShopRepository.flush();
    }

    private Integer scrapCard(Product productCard) {
        if (productCard == null || productCard.getDescription() == null || StringUtils.isBlank(productCard.getDescription().getName())) {
            log.warn("Product card or its name is null: {}", productCard);
            return null;
        }
        String cardNameRaw = productCard.getDescription().getName();
        int firstIndex = cardNameRaw.indexOf("-");
        int lastIndex = cardNameRaw.lastIndexOf("-");
        String cardNameHtml;
        if (firstIndex > 0 && lastIndex > 0 && firstIndex != lastIndex) {
            cardNameHtml = cardNameRaw.substring(firstIndex + 1, lastIndex);
        } else if (firstIndex > 0 && lastIndex < 0) {
            cardNameHtml = cardNameRaw.substring(firstIndex + 1);
        } else {
            cardNameHtml = cardNameRaw;
        }
        int groupIndex = cardNameHtml.indexOf("[");
        if (groupIndex > 0) {
            cardNameHtml = cardNameHtml.substring(0, groupIndex);
        }
        int advancedIndex = cardNameHtml.toLowerCase().indexOf("(adv)");
        boolean advanced = advancedIndex > 0;
        if (advanced) {
            cardNameHtml = cardNameHtml.substring(0, advancedIndex);
        }
        if (cardNameHtml.startsWith("Promo -")) {
            cardNameHtml = cardNameHtml.substring(7);
        }
        cardNameHtml = cardNameHtml.replace("_", ":");
        final String cardName = StringUtils.trim(StringEscapeUtils.unescapeXml(cardNameHtml));
        List<TextSearch> cards = deckCardRepository.search(cardName, advanced);
        if (CollectionUtils.isEmpty(cards)) {
            log.warn("Unable to found card with name '{}' with full name {}", cardName, cardNameRaw);
            return null;
        }
        final TextSearch card;
        if (cards.size() > 1) {
            Optional<TextSearch> exactCard = cards.stream().filter(cardSearch -> cardSearch.getName().replaceAll(SPECIAL_CHARACTERS, "").equalsIgnoreCase(cardName.replaceAll(SPECIAL_CHARACTERS, ""))).findFirst();
            if (exactCard.isPresent()) {
                card = exactCard.get();
            } else {
                log.warn("Multiple finds for '{}' with raw '{}': {}", cardName, cardNameRaw, cards.stream().map(TextSearch::getName).toList());
                return null;
            }
        } else {
            card = cards.getFirst();
        }

        String link = getLink(productCard);
        BigDecimal price = getPrice(cardName, productCard);
        if (price != null && price.compareTo(BigDecimal.TEN) >= 0) {
            log.warn("Price too high for '{}': {}", cardNameRaw, price);
            return null;
        }
        if (link == null) {
            return null;
        }

        CardShopEntity cardShop = CardShopEntity.builder()
                .cardId(card.getId())
                .link(link)
                .platform(PLATFORM)
                .set(SET)
                .price(price)
                .currency(DOLLAR)
                .inStock(true)
                .build();
        log.trace("Scrapped card {}", cardShop);
        List<CardShopEntity> cardShopList = cardShopRepository.findByCardIdAndPlatform(cardShop.getCardId(), PLATFORM);
        if (CollectionUtils.isEmpty(cardShopList)) {
            cardShopRepository.saveAndFlush(cardShop);
        } else {
            CardShopEntity current = cardShopList.getFirst();
            cardShop.setId(current.getId());
            if (!cardShop.equals(current)) {
                cardShopRepository.saveAndFlush(cardShop);
            }
        }
        return cardShop.getCardId();
    }

    private static String getLink(Product productCard) {
        if (productCard.getDescription().getSlug() == null || productCard.getProductId() == null) {
            log.warn("Product link is null: {}", productCard);
            return null;
        }
        return "https://www.drivethrucards.com/product/" + productCard.getProductId() + "/" + productCard.getDescription().getSlug() + "?affiliate_id=2900918";
    }

    private static BigDecimal getPrice(String cardName, Product productCard) {
        if (productCard.getLowestPrintPrice() == null) {
            log.warn("No price information for {}", cardName);
            return null;
        }
        return productCard.getLowestPrintPrice();
    }


    private Product getProduct(int productId) {
        return dtcClient.getProduct(productId, GROUP_ID, SITE_ID);
    }

    private List<Product> getProducts(int page) {
        return dtcClient.getProducts(GROUP_ID, SITE_ID, 1, false, BLACK_CHANTRY_CATEGORY_ID, page, "desc");
    }
}
