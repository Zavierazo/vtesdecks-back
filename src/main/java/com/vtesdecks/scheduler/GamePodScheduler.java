package com.vtesdecks.scheduler;

import com.vtesdecks.db.CardShopMapper;
import com.vtesdecks.db.TextSearchMapper;
import com.vtesdecks.db.model.DbCardShop;
import com.vtesdecks.db.model.DbTextSearch;
import com.vtesdecks.integration.GamePodClient;
import com.vtesdecks.model.shopify.Product;
import com.vtesdecks.model.shopify.ProductsResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GamePodScheduler {
    private static final String PLATFORM = "GP";
    private static final String SET = "POD:GP";

    @Autowired
    private TextSearchMapper textSearchMapper;

    @Autowired
    private CardShopMapper cardShopMapper;

    @Autowired
    private GamePodClient gamePodClient;

    @Scheduled(cron = "0 0 0 * * 0")
    public void scrapCards() {
        log.info("Starting GP scrapping...");
        for (int pageIndex = 1; pageIndex <= 50; pageIndex++) {
            try {
                ProductsResponse productsResponse = gamePodClient.getProducts(250, pageIndex);
                parsePage(productsResponse.getProducts());
            } catch (Exception e) {
                log.error("Error scrapping GP page " + pageIndex, e);
            }
        }
        log.info("GP scrap finished!");
    }

    private void parsePage(List<Product> products) {
        for (Product product : products) {
            try {
                if (product.getTags() != null && (!product.getTags().contains("Vampire") || product.getTags().contains("Tapete") || product.getTags().contains("Accesorios") || product.getTags().contains("Tokens"))) {
                    log.warn("Ignoring product {}", product.getHandle());
                    continue;
                }
                scrapCard(product);
            } catch (Exception e) {
                log.error("Error scrapping GP element {}", product, e);
            }
        }
    }

    private void scrapCard(Product productCard) {
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
        cardNameRaw = cardNameRaw.replace("_", ":");
        final String cardName = StringUtils.trim(cardNameRaw);
        List<DbTextSearch> cards = textSearchMapper.search(cardName, advanced);
        if (CollectionUtils.isEmpty(cards)) {
            log.warn("Unable to found card with name '{}' with full name {}", cardName, cardNameRaw);
            return;
        }
        final DbTextSearch card;
        if (cards.size() > 1) {
            Optional<DbTextSearch> exactCard = cards.stream()
                    .filter(cardSearch -> cardSearch.getName().equalsIgnoreCase(cardName))
                    .findFirst();
            if (exactCard.isPresent()) {
                card = exactCard.get();
            } else {
                log.warn("Multiple finds for '{}' with raw '{}': {}", cardName, cardNameRaw, cards.stream().map(DbTextSearch::getName).collect(Collectors.toList()));
                card = cards.get(0);
            }
        } else {
            card = cards.get(0);
        }

        String link = getLink(productCard);
        BigDecimal price = getPrice(productCard);

        DbCardShop cardShop = DbCardShop.builder()
                .cardId(card.getId())
                .link(link)
                .platform(PLATFORM)
                .set(SET)
                .price(price)
                .build();
        log.trace("Scrapped card {}", cardShop);
        List<DbCardShop> cardShopList = cardShopMapper.selectByCardIdAndPlatform(cardShop.getCardId(), PLATFORM);
        if (CollectionUtils.isEmpty(cardShopList)) {
            cardShopMapper.insert(cardShop);
        } else {
            DbCardShop current = cardShopList.get(0);
            cardShop.setId(current.getId());
            if (!cardShop.equals(current)) {
                cardShopMapper.update(cardShop);
            }
        }
    }

    private static String getLink(Product productCard) {
        return "https://www.gamepod.es/products/" + productCard.getHandle();
    }

    private static BigDecimal getPrice(Product productCard) {
        return !CollectionUtils.isEmpty(productCard.getVariants()) ? productCard.getVariants().get(0).getPrice() : null;
    }
}
