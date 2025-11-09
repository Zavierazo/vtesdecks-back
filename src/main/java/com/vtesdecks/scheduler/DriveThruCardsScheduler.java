package com.vtesdecks.scheduler;

import com.vtesdecks.integration.FlareSolverr;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.entity.extra.TextSearch;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.model.flaresolverr.FlareRequest;
import com.vtesdecks.model.flaresolverr.FlareResponse;
import feign.RetryableException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Optional;

import static org.apache.commons.lang3.ObjectUtils.isEmpty;

@Slf4j
@Component
public class DriveThruCardsScheduler {
    private static final String PLATFORM = "DTC";
    private static final String SET = "POD:DTC";
    private static final String SEARCH_URL = "https://www.drivethrucards.com/browse/pub/12056/Black-Chantry-Productions/subcategory/30619_34256/VTES-Legacy-Card-Singles?sort=4a&pfrom=0.01&pto=0.59&page=%s";
    private static final String DOLLAR = "USD";
    private static final String SPECIAL_CHARACTERS = "[_,:\"'”\\s]";


    @Autowired
    private DeckCardRepository deckCardRepository;
    @Autowired
    private CardShopRepository cardShopRepository;
    @Autowired
    private FlareSolverr flareSolverr;

    @Scheduled(cron = "0 0 0 * * *")
    public void scrapCards() {
        log.info("Starting DTC scrapping...");
        List<CardShopEntity> currentCards = cardShopRepository.findByPlatform(PLATFORM);
        String session = null;
        try {
            session = createSession();
            int numPages = getNumPages(session);
            for (int pageIndex = 1; pageIndex <= numPages; pageIndex++) {
                try {
                    Document page = getDocument(String.format(SEARCH_URL, pageIndex), session);
                    if (page != null) {
                        parsePage(page, currentCards);
                    }
                } catch (Exception e) {
                    log.error("Error scrapping DTC page {}", pageIndex, e);
                }
            }
            if (!isEmpty(currentCards.size())) {
                cleanOutdatedCards(currentCards, session);
            }
        } finally {
            destroySession(session);
        }
        log.info("DTC scrap finished!");
    }

    private void cleanOutdatedCards(List<CardShopEntity> currentCards, String session) {
        for (CardShopEntity cardShop : currentCards) {
            try {
                Document page = getDocument(cardShop.getLink(), session);
                String pageTitle = page != null ? page.title() : null;
                if (" -  | DriveThruCards.com".equalsIgnoreCase(pageTitle)) {
                    log.warn("Card {} no longer exists in shop {}", cardShop.getCardId(), cardShop.getLink());
                    cardShopRepository.delete(cardShop);
                } else {
                    log.debug("Card {} still exists in shop {}", cardShop.getCardId(), cardShop.getLink());
                }
            } catch (Exception e) {
                log.error("Error scrapping DTC page {}", cardShop.getLink(), e);
            }
        }
        cardShopRepository.flush();
    }

    private int getNumPages(String session) {
        try {
            Document page = getDocument(String.format(SEARCH_URL, 1), session);
            if (page == null) return 0;
            Element pagesElement = page.selectFirst("[name=pages]");
            return pagesElement != null ? pagesElement.children().size() : 0;
        } catch (Exception e) {
            log.error("Error getting number of pages on DTC", e);
            return 0;
        }
    }

    private void parsePage(Document page, List<CardShopEntity> currentCards) {
        for (Element productCard : page.select("tr.dtrpgListing-row")) {
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

    private Integer scrapCard(Element productCard) {
        Element cardNameElement = productCard.selectFirst("td h1 a span");
        String cardNameRaw = cardNameElement != null ? cardNameElement.ownText() : "";
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

        CardShopEntity cardShop = CardShopEntity.builder().cardId(card.getId()).link(link).platform(PLATFORM).set(SET).price(price).currency(DOLLAR).build();
        log.trace("Scrapped card {}", cardShop);
        List<CardShopEntity> cardShopList = cardShopRepository.findByCardIdAndPlatform(cardShop.getCardId(), PLATFORM);
        if (CollectionUtils.isEmpty(cardShopList)) {
            cardShopRepository.save(cardShop);
        } else {
            CardShopEntity current = cardShopList.getFirst();
            cardShop.setId(current.getId());
            if (!cardShop.equals(current)) {
                cardShopRepository.save(cardShop);
            }
        }
        return cardShop.getCardId();
    }

    private static String getLink(Element productCard) {
        Element cardLinkElement = productCard.selectFirst("td h1 a");
        String cardLink = cardLinkElement != null ? cardLinkElement.attr("href") : "";
        return cardLink + "&affiliate_id=2900918";
    }

    private static BigDecimal getPrice(String cardName, Element productCard) {
        try {
            Element priceElement = productCard.select("td").get(2);
            String priceString = priceElement.text();
            return new BigDecimal(priceString.substring(1));
        } catch (Exception e) {
            log.warn("Unable to obtain price for {}", cardName, e);
            return null;
        }
    }

    private Document getDocument(String url, String session) {
        try {
            FlareResponse flareResponse = flareSolverr.getPage(FlareRequest.builder().cmd("request.get").session(session).url(url).maxTimeout(120000).build());
            if (flareResponse != null
                    && "ok".equalsIgnoreCase(flareResponse.getStatus())
                    && flareResponse.getSolution() != null
                    && flareResponse.getSolution().getResponse() != null) {
                return Jsoup.parse(flareResponse.getSolution().getResponse());
            } else {
                log.warn("FlareSolverr did not return a valid response for URL {}: {}", url, flareResponse);
            }
        } catch (RetryableException e) {
            if (e.getCause() != null && e.getCause() instanceof SocketTimeoutException) {
                log.warn("Unable to solve challenge for {}: {}", url, e.getCause().getMessage());
            } else {
                log.error("Error getting document from url {}", url, e);
            }
        } catch (Exception e) {
            log.error("Error getting document from url {}", url, e);
        }
        return null;
    }

    private String createSession() {
        try {
            FlareResponse flareResponse = flareSolverr.getPage(FlareRequest.builder().cmd("sessions.create").build());
            if (flareResponse != null && "ok".equalsIgnoreCase(flareResponse.getStatus()) && flareResponse.getSession() != null) {
                return flareResponse.getSession();
            } else {
                log.warn("FlareSolverr unable to create session");
            }
        } catch (Exception e) {
            log.error("FlareSolverr unable to create session", e);
        }
        return null;
    }

    private void destroySession(String session) {
        try {
            FlareResponse flareResponse = flareSolverr.getPage(FlareRequest.builder().cmd("sessions.destroy").session(session).build());
            if (flareResponse == null || !"ok".equalsIgnoreCase(flareResponse.getStatus())) {
                log.warn("FlareSolverr unable to destroy session");
            }
        } catch (Exception e) {
            log.error("FlareSolverr unable to destroy session", e);
        }
    }
}
