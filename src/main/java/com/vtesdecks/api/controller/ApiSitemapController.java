package com.vtesdecks.api.controller;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.model.ApiDeckType;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.DeckSort;
import com.vtesdecks.service.DeckService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/sitemap.xml")
@RequiredArgsConstructor
@Slf4j
public class ApiSitemapController {

    private static final String BASE_URL = "https://vtesdecks.com";
    private static final DateTimeFormatter W3C_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String FREQ_DAILY = "daily";
    private static final String FREQ_HOURLY = "hourly";
    private static final String FREQ_WEEKLY = "weekly";
    private static final String FREQ_MONTHLY = "monthly";
    private static final String FREQ_YEARLY = "yearly";

    /**
     * Static pages: loc -> [changefreq, priority]
     */
    private static final Map<String, String[]> STATIC_PAGES = new LinkedHashMap<>();

    static {
        STATIC_PAGES.put("/", new String[]{FREQ_DAILY, "1.0"});
        STATIC_PAGES.put("/decks", new String[]{FREQ_HOURLY, "0.9"});
        STATIC_PAGES.put("/metagame", new String[]{FREQ_WEEKLY, "0.8"});
        STATIC_PAGES.put("/statistics", new String[]{FREQ_WEEKLY, "0.7"});
        STATIC_PAGES.put("/cards/crypt", new String[]{FREQ_MONTHLY, "0.7"});
        STATIC_PAGES.put("/cards/library", new String[]{FREQ_MONTHLY, "0.7"});
        STATIC_PAGES.put("/vtesdle", new String[]{FREQ_DAILY, "0.7"});
        STATIC_PAGES.put("/advent", new String[]{FREQ_YEARLY, "0.5"});
        STATIC_PAGES.put("/vtes-ai", new String[]{FREQ_MONTHLY, "0.6"});
        STATIC_PAGES.put("/proxy-generator", new String[]{FREQ_MONTHLY, "0.5"});
        STATIC_PAGES.put("/contact", new String[]{FREQ_YEARLY, "0.3"});
        STATIC_PAGES.put("/changelog", new String[]{FREQ_MONTHLY, "0.4"});
    }

    private final DeckService deckService;

    @GetMapping(produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        String today = LocalDateTime.now().format(W3C_DATE);
        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Static pages
        for (Map.Entry<String, String[]> entry : STATIC_PAGES.entrySet()) {
            xml.append("  <url>\n");
            xml.append("    <loc>").append(escapeXml(BASE_URL + entry.getKey())).append("</loc>\n");
            xml.append("    <lastmod>").append(today).append("</lastmod>\n");
            xml.append("    <changefreq>").append(entry.getValue()[0]).append("</changefreq>\n");
            xml.append("    <priority>").append(entry.getValue()[1]).append("</priority>\n");
            xml.append("  </url>\n");
        }

        // Tournament decks
        appendDecks(xml, ApiDeckType.TOURNAMENT);

        // Community decks
        appendDecks(xml, ApiDeckType.COMMUNITY);

        xml.append("</urlset>");
        return xml.toString();
    }

    private void appendDecks(StringBuilder xml, ApiDeckType deckType) {
        try {
            DeckQuery query = DeckQuery.builder()
                    .apiType(deckType)
                    .order(DeckSort.NEWEST)
                    .build();
            ResultSet<Deck> decks = deckService.getDecks(query);
            Stream<Deck> deckStream = decks.stream();
            deckStream.forEach(deck -> {
                String lastmod = resolveLastmod(deck);
                xml.append("  <url>\n");
                xml.append("    <loc>").append(escapeXml(BASE_URL + "/deck/" + deck.getId())).append("</loc>\n");
                xml.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
                xml.append("    <changefreq>").append(FREQ_MONTHLY).append("</changefreq>\n");
                xml.append("    <priority>0.6</priority>\n");
                xml.append("  </url>\n");
            });
        } catch (Exception e) {
            log.warn("Could not fetch {} decks for sitemap: {}", deckType, e.getMessage());
        }
    }

    private String resolveLastmod(Deck deck) {
        if (deck.getModifyDate() != null) {
            return deck.getModifyDate().format(W3C_DATE);
        }
        if (deck.getCreationDate() != null) {
            return deck.getCreationDate().format(W3C_DATE);
        }
        return LocalDateTime.now().format(W3C_DATE);
    }

    private String escapeXml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}


