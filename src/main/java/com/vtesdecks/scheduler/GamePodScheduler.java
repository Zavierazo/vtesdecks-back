package com.vtesdecks.scheduler;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.vtesdecks.db.CardShopMapper;
import com.vtesdecks.db.TextSearchMapper;
import com.vtesdecks.db.model.DbCardShop;
import com.vtesdecks.db.model.DbTextSearch;
import com.vtesdecks.integration.GamePodClient;
import com.vtesdecks.model.shopify.Product;
import com.vtesdecks.model.shopify.ProductsResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Slf4j
@Component
public class GamePodScheduler {
    private static final String PLATFORM = "GP";
    private static final String SET = "POD:GP";
    private static final String EURO = "EUR";
    private static final List<String> IGNORED_TITLES = List.of(" mazo de ", " mazos de ", "Pack de ", "PROMO PACK", "(Harmen)", "starter deck");
    private static final String SPECIAL_CHARACTERS = "[_,:\"'”\\s]";

    @Autowired
    private TextSearchMapper textSearchMapper;

    @Autowired
    private CardShopMapper cardShopMapper;

    @Autowired
    private GamePodClient gamePodClient;

    @Scheduled(cron = "0 0 0 * * 0")
    public void scrapCards() {
        log.info("Starting GP scrapping...");
        List<DbCardShop> currentCards = cardShopMapper.selectByPlatform(PLATFORM);
        for (int pageIndex = 1; pageIndex <= 50; pageIndex++) {
            try {
                ProductsResponse productsResponse = gamePodClient.getProducts(250, pageIndex);
                parsePage(productsResponse.getProducts(), currentCards);
            } catch (Exception e) {
                log.error("Error scrapping GP page " + pageIndex, e);
            }
        }
        if (!isEmpty(currentCards.size())) {
            cleanOutdatedCards(currentCards);
        }
        log.info("GP scrap finished!");
    }

    private void cleanOutdatedCards(List<DbCardShop> currentCards) {
        WebClient client = configureClient();
        for (DbCardShop cardShop : currentCards) {
            try {
                client.getPage(cardShop.getLink());
                log.warn("Card {} still exists in shop {}", cardShop.getCardId(), cardShop.getLink());
            } catch (FailingHttpStatusCodeException e) {
                if (e.getStatusCode() == 404) {
                    log.warn("Card {} no longer exists in shop {}", cardShop.getCardId(), cardShop.getLink());
                    cardShopMapper.delete(cardShop.getId());
                } else {
                    log.error("Error scrapping GP page {}", cardShop.getLink(), e);
                }
            } catch (IOException e) {
                log.error("Error scrapping GP page {}", cardShop.getLink(), e);
            }

        }
    }

    private WebClient configureClient() {
        WebClient client = new WebClient();
        client.getOptions().setCssEnabled(false);
        client.getOptions().setJavaScriptEnabled(false);

        return client;
    }

    private void parsePage(List<Product> products, List<DbCardShop> currentCards) {
        for (Product product : products) {
            try {
                if (product.getTags() != null && (!product.getTags().contains("Vampire") || product.getTags().contains("Tapete") || product.getTags().contains("Accesorios") || product.getTags().contains("Tokens"))) {
                    log.trace("Ignoring product {}", product.getHandle());
                    continue;
                }
                if (product.getTitle() != null && IGNORED_TITLES.stream().anyMatch(title -> product.getTitle().contains(title))) {
                    log.trace("Ignoring product {}", product.getHandle());
                    continue;
                }
                Integer cardId = scrapCard(product);
                if (cardId != null) {
                    currentCards.removeIf(cardShop -> cardShop.getCardId().equals(cardId));
                }
            } catch (Exception e) {
                log.error("Error scrapping GP element {}", product, e);
            }
        }
    }

    private Integer scrapCard(Product productCard) {
        String cardNameRaw = productCard.getTitle();
        int advancedIndex = cardNameRaw.toLowerCase().indexOf("(adv)");
        boolean advanced = advancedIndex > 0;
        if (advanced) {
            cardNameRaw = cardNameRaw.substring(0, advancedIndex);
        }
        int groupIndex = cardNameRaw.toLowerCase().indexOf("[");
        if (groupIndex > 0) {
            cardNameRaw = cardNameRaw.substring(0, groupIndex);
        }
        int typeIndex = cardNameRaw.toLowerCase().indexOf(" - ");
        if (typeIndex > 0) {
            cardNameRaw = cardNameRaw.substring(0, typeIndex);
        }
        int typeAltIndex = cardNameRaw.toLowerCase().indexOf(" – ");
        if (typeAltIndex > 0) {
            cardNameRaw = cardNameRaw.substring(0, typeAltIndex);
        }

        List<DbTextSearch> cards = textSearchMapper.search(cardNameRaw.replace("_", ":"), advanced);
        if (CollectionUtils.isEmpty(cards)) {
            log.warn("Unable to found card with name '{}'", cardNameRaw);
            return null;
        }

        final DbTextSearch card;
        if (cards.size() > 1) {
            final String cardName = cardNameRaw.replaceAll(SPECIAL_CHARACTERS, "").trim();
            Optional<DbTextSearch> exactCard = cards.stream()
                    .filter(cardSearch -> cardSearch.getName().replaceAll(SPECIAL_CHARACTERS, "")
                            .equalsIgnoreCase(cardName))
                    .findFirst();
            if (exactCard.isPresent()) {
                card = exactCard.get();
            } else {
                log.warn("Multiple finds for '{}' with raw '{}': {}", cardName, cardNameRaw, cards.stream().map(DbTextSearch::getName).toList());
                return null;
            }
        } else {
            card = cards.getFirst();
        }

        String link = getLink(productCard);
        BigDecimal price = getPrice(productCard);
        if (price != null && price.compareTo(BigDecimal.TEN) >= 0) {
            log.warn("Price too high for '{}': {}", cardNameRaw, price);
            return null;
        }

        DbCardShop cardShop = DbCardShop.builder()
                .cardId(card.getId())
                .link(link)
                .platform(PLATFORM)
                .set(SET)
                .price(price)
                .currency(EURO)
                .build();
        log.trace("Scrapped card {}", cardShop);
        List<DbCardShop> cardShopList = cardShopMapper.selectByCardIdAndPlatform(cardShop.getCardId(), PLATFORM);
        if (CollectionUtils.isEmpty(cardShopList)) {
            cardShopMapper.insert(cardShop);
        } else {
            DbCardShop current = cardShopList.getFirst();
            cardShop.setId(current.getId());
            if (!cardShop.equals(current)) {
                cardShopMapper.update(cardShop);
            }
        }
        return cardShop.getCardId();
    }

    private static String getLink(Product productCard) {
        return "https://www.gamepod.es/products/" + productCard.getHandle();
    }

    private static BigDecimal getPrice(Product productCard) {
        return !CollectionUtils.isEmpty(productCard.getVariants()) ? productCard.getVariants().getFirst().getPrice() : null;
    }
}
