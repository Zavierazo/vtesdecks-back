package com.vtesdecks.scheduler;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.vtesdecks.db.CardShopMapper;
import com.vtesdecks.db.TextSearchMapper;
import com.vtesdecks.db.model.DbCardShop;
import com.vtesdecks.db.model.DbTextSearch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DriveThruCardsScheduler {
    private static final String PLATFORM = "DTC";
    private static final String SET = "POD:DTC";
    private static final String SEARCH_URL = "https://www.drivethrucards.com/browse/pub/12056/Black-Chantry-Productions/subcategory/30619_34256/VTES-Legacy-Card-Singles?sort=4a&pfrom=0.01&pto=0.59&page=%s";


    @Autowired
    private TextSearchMapper textSearchMapper;

    @Autowired
    private CardShopMapper cardShopMapper;

    @Scheduled(cron = "0 0 0 * * 0")
    public void scrapCards() {
        log.info("Starting DTC scrapping...");
        WebClient client = configureClient();
        int numPages = getNumPages(client);
        for (int pageIndex = 1; pageIndex <= numPages; pageIndex++) {
            try {
                HtmlPage page = client.getPage(String.format(SEARCH_URL, pageIndex));
                parsePage(page);
            } catch (Exception e) {
                log.error("Error scrapping DTC page " + pageIndex, e);
            }
        }
        log.info("DTC scrap finished!");
    }

    private WebClient configureClient() {
        WebClient client = new WebClient();
        client.getOptions().setCssEnabled(false);
        client.getOptions().setJavaScriptEnabled(false);

        return client;
    }

    private int getNumPages(WebClient client) {
        try {
            HtmlPage page = client.getPage(String.format(SEARCH_URL, 1));
            return page.getElementByName("pages").getChildElementCount();
        } catch (Exception e) {
            log.error("Error getting number of pages on DTC", e);
            return 0;
        }
    }

    private void parsePage(HtmlPage page) {
        for (HtmlElement productCard : (List<HtmlElement>) page.getByXPath("//tr[@class='dtrpgListing-row']")) {
            try {
                scrapCard(productCard);
            } catch (Exception e) {
                log.error("Error scrapping DTC element {}", productCard, e);
            }
        }
    }

    private void scrapCard(HtmlElement productCard) {
        HtmlElement cardNameElement = (HtmlElement) productCard.getByXPath(".//td/h1/a/span").get(0);
        String rawCardName = cardNameElement.getFirstChild().getNodeValue();
        int firstIndex = rawCardName.indexOf("-");
        int lastIndex = rawCardName.lastIndexOf("-");
        String cardNameHtml;
        if (firstIndex > 0 && lastIndex > 0 && firstIndex != lastIndex) {
            cardNameHtml = rawCardName.substring(firstIndex + 1, lastIndex);
        } else if (firstIndex > 0 && lastIndex < 0) {
            cardNameHtml = rawCardName.substring(firstIndex + 1);
        } else {
            cardNameHtml = rawCardName;
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
        cardNameHtml = cardNameHtml.replace("_", ":");
        final String cardName = StringUtils.trim(StringEscapeUtils.unescapeXml(cardNameHtml));
        List<DbTextSearch> cards = textSearchMapper.search(cardName, advanced);
        if (CollectionUtils.isEmpty(cards)) {
            log.warn("Unable to found card with name '{}' with full name {}", cardName, rawCardName);
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
                log.warn("Multiple finds for '{}' with raw '{}': {}", cardName, rawCardName, cards.stream().map(DbTextSearch::getName).collect(Collectors.toList()));
                card = cards.get(0);
            }
        } else {
            card = cards.get(0);
        }

        String link = getLink(productCard);
        BigDecimal price = getPrice(cardName, productCard);

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

    private static String getLink(HtmlElement productCard) {
        HtmlElement cardLinkElement = (HtmlElement) productCard.getByXPath(".//td/h1/a").get(0);
        String cardLink = cardLinkElement.getAttribute("href");
        return cardLink + "&affiliate_id=2900918";
    }

    private static BigDecimal getPrice(String cardName, HtmlElement productCard) {
        try {
            HtmlElement priceElement = (HtmlElement) productCard.getByXPath(".//td").get(2);
            String priceString = priceElement.getFirstChild().getTextContent();
            return new BigDecimal(priceString.substring(1));
        } catch (Exception e) {
            log.warn("Unable to obtain price for {}", cardName, e);
            return null;
        }
    }
}
