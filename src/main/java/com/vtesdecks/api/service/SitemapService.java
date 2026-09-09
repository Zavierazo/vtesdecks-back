package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.redis.entity.DeckArchetype;
import com.vtesdecks.cache.redis.repositories.DeckArchetypeRedisRepository;
import com.vtesdecks.jpa.repositories.CollectionBinderRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.ApiDeckType;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.service.DeckService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class SitemapService {
    public static final int PAGE_SIZE = 50_000;
    public static final int MAX_BYTES = 50 * 1024 * 1024;
    private static final String SITE = "https://vtesdecks.com";
    private static final String SITEMAPS = "https://api.vtesdecks.com/sitemap/";
    private static final String NAMESPACE = "http://www.sitemaps.org/schemas/sitemap/0.9";
    private static final List<String> STATIC_PAGES = List.of(
            "/", "/decks", "/metagame", "/statistics", "/cards/crypt", "/cards/library",
            "/vtesdle", "/advent", "/proxy-generator", "/contact", "/changelog",
            "/terms", "/privacy-policy", "/tutorial", "/tutorial/play", "/tutorial/resources");

    private final DeckService deckService;
    private final DeckArchetypeRedisRepository archetypes;
    private final UserRepository users;
    private final CollectionBinderRepository binders;

    public record Entry(String url, LocalDateTime modified) {}

    /** One sorted, deduplicated enumeration per document. Failures propagate, never publish partial XML. */
    @Transactional(readOnly = true)
    public List<Entry> entries() {
        SortedMap<String, Entry> entries = new TreeMap<>();
        Set<Integer> publicDeckOwners = new HashSet<>();
        STATIC_PAGES.forEach(path -> add(entries, path, null));
        // The anonymous ALL query reuses the index's published/non-deleted visibility rules.
        try (ResultSet<Deck> decks = deckService.getDecks(DeckQuery.builder().apiType(ApiDeckType.ALL).build())) {
            decks.stream().filter(Deck::isPublished).forEach(deck -> {
                add(entries, "/deck/" + segment(deck.getId()),
                        deck.getModifyDate() != null ? deck.getModifyDate() : deck.getCreationDate());
                if (deck.getUser() != null && deck.getUser().getId() != null) {
                    publicDeckOwners.add(deck.getUser().getId());
                }
            });
        }
        for (DeckArchetype archetype : archetypes.findAll()) {
            if (archetype.getId() != null && archetype.getId() > 0 && Boolean.TRUE.equals(archetype.getEnabled())) {
                add(entries, "/metagame/" + archetype.getId(), archetype.getModificationDate());
            }
        }
        Set<Integer> nonEmptyPublicWishlists = new HashSet<>(users.findNonEmptyPublicWishlistUserIdsForSitemap());
        for (UserRepository.SitemapUser user : users.findSitemapUsers()) {
            String username = user.getUsername();
            if (username == null || username.isBlank()) continue;
            // /user/settings is an account route, even if an old account used that name.
            if (publicDeckOwners.contains(user.getId()) && !"settings".equals(username)) {
                add(entries, "/user/" + segment(username), null);
            }
            if (Boolean.TRUE.equals(user.getWishlistPublicVisibility()) && nonEmptyPublicWishlists.contains(user.getId())) {
                add(entries, "/collections/users/" + segment(username) + "/wishlist", null);
            }
        }
        for (String hash : binders.findPublicHashesForSitemap()) {
            if (hash != null && !hash.isBlank() && !hash.matches("[0-9]+")) {
                add(entries, "/collection/binders/" + segment(hash), null);
            }
        }
        // Profile timestamps include account-only changes; binder timestamps miss card edits.
        // Neither is a reliable lastmod for the public page, so omit those dates.
        return List.copyOf(entries.values());
    }

    public String sitemap(List<Entry> entries) {
        if (entries.size() <= PAGE_SIZE) {
            String document = urlset(entries);
            if (document.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES) return document;
        }
        List<List<Entry>> pages = partition(entries, PAGE_SIZE, MAX_BYTES);
        return index(pages.size());
    }

    private String index(int count) {
        return xml("sitemapindex", writer -> {
            for (int page = 1; page <= count; page++) {
                writer.writeStartElement("sitemap");
                element(writer, "loc", SITEMAPS + page + ".xml");
                writer.writeEndElement();
            }
        });
    }

    public String page(List<Entry> entries, int page) {
        if (page < 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        List<List<Entry>> pages = partition(entries, PAGE_SIZE, MAX_BYTES);
        if (page > pages.size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return urlset(pages.get(page - 1));
    }

    /** Count the actual escaped UTF-8 XML, including the document envelope. */
    List<List<Entry>> partition(List<Entry> entries, int maxUrls, int maxBytes) {
        List<List<Entry>> pages = new ArrayList<>();
        int envelopeBytes = urlset(List.of()).getBytes(StandardCharsets.UTF_8).length;
        int bytes = envelopeBytes;
        int start = 0;
        for (int i = 0; i < entries.size(); i++) {
            int entryBytes = urlset(List.of(entries.get(i))).getBytes(StandardCharsets.UTF_8).length - envelopeBytes;
            if ((long) envelopeBytes + entryBytes > maxBytes) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Sitemap entry exceeds document size limit");
            }
            if (i - start == maxUrls || (long) bytes + entryBytes > maxBytes) {
                pages.add(entries.subList(start, i));
                start = i;
                bytes = envelopeBytes;
            }
            bytes += entryBytes;
        }
        // Even an empty catalog is a valid single urlset, not an empty index.
        pages.add(entries.subList(start, entries.size()));
        return pages;
    }

    private String urlset(List<Entry> entries) {
        return xml("urlset", writer -> {
            for (Entry entry : entries) {
                writer.writeStartElement("url");
                element(writer, "loc", entry.url());
                if (entry.modified() != null) element(writer, "lastmod", entry.modified().toLocalDate().toString());
                writer.writeEndElement();
            }
        });
    }

    private void add(SortedMap<String, Entry> entries, String path, LocalDateTime modified) {
        String url = SITE + path;
        entries.putIfAbsent(url, new Entry(url, modified));
    }

    private String segment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private void element(XMLStreamWriter writer, String name, String value) throws XMLStreamException {
        writer.writeStartElement(name);
        writer.writeCharacters(value);
        writer.writeEndElement();
    }

    private String xml(String root, XmlBody body) {
        StringWriter output = new StringWriter();
        try {
            XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(output);
            writer.writeStartDocument("UTF-8", "1.0");
            writer.writeStartElement(root);
            writer.writeDefaultNamespace(NAMESPACE);
            body.write(writer);
            writer.writeEndElement();
            writer.writeEndDocument();
            writer.close();
            return output.toString();
        } catch (XMLStreamException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to generate sitemap", e);
        }
    }

    @FunctionalInterface
    private interface XmlBody {
        void write(XMLStreamWriter writer) throws XMLStreamException;
    }
}
