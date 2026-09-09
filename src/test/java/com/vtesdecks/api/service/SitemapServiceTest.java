package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.redis.entity.DeckArchetype;
import com.vtesdecks.cache.redis.repositories.DeckArchetypeRedisRepository;
import com.vtesdecks.jpa.repositories.CollectionBinderRepository;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.service.DeckService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SitemapServiceTest {
    @Mock DeckService decks;
    @Mock DeckArchetypeRedisRepository archetypes;
    @Mock UserRepository users;
    @Mock CollectionBinderRepository binders;
    @Mock ResultSet<Deck> results;
    @InjectMocks SitemapService service;

    private record User(Integer id, String username, Boolean wishlistPublicVisibility) implements UserRepository.SitemapUser {
        @Override public Integer getId() { return id; }
        @Override public String getUsername() { return username; }
        @Override public Boolean getWishlistPublicVisibility() { return wishlistPublicVisibility; }
    }

    @Test
    void enumeratesPublicResourcesOnlyAndUsesReliableDates() {
        Deck published = new Deck();
        published.setId("public-deck");
        published.setPublished(true);
        UserEntity alice = new UserEntity();
        alice.setId(1);
        published.setUser(alice);
        published.setCreationDate(LocalDateTime.of(2024, 1, 2, 0, 0));
        published.setModifyDate(LocalDateTime.of(2026, 7, 8, 12, 0));
        Deck privateDeck = new Deck();
        privateDeck.setId("private-deck");
        UserEntity bob = new UserEntity();
        bob.setId(2);
        privateDeck.setUser(bob);
        when(decks.getDecks(any())).thenReturn(results);
        when(results.stream()).thenReturn(Stream.of(published, privateDeck, published));
        when(archetypes.findAll()).thenReturn(List.of(
                DeckArchetype.builder().id(2).enabled(true).build(),
                DeckArchetype.builder().id(3).enabled(false).build(),
                DeckArchetype.builder().id(0).enabled(true).build()));
        when(users.findSitemapUsers()).thenReturn(List.of(new User(1, "alice", true), new User(2, "bob", false), new User(3, "carol", true), new User(4, "settings", false), new User(5, "empty", true)));
        when(users.findNonEmptyPublicWishlistUserIdsForSitemap()).thenReturn(List.of(1, 3));
        when(binders.findPublicHashesForSitemap()).thenReturn(List.of("public-hash", "public-hash", "", "123"));

        List<SitemapService.Entry> entries = service.entries();
        List<String> urls = entries.stream().map(SitemapService.Entry::url).toList();
        assertTrue(urls.contains("https://vtesdecks.com/deck/public-deck"));
        assertFalse(urls.contains("https://vtesdecks.com/deck/private-deck"));
        assertTrue(urls.contains("https://vtesdecks.com/metagame/2"));
        assertFalse(urls.contains("https://vtesdecks.com/metagame/3"));
        assertFalse(urls.contains("https://vtesdecks.com/metagame/0"));
        assertTrue(urls.contains("https://vtesdecks.com/user/alice"));
        assertFalse(urls.contains("https://vtesdecks.com/user/bob"));
        assertFalse(urls.contains("https://vtesdecks.com/user/carol"));
        assertFalse(urls.contains("https://vtesdecks.com/user/settings"));
        assertTrue(urls.contains("https://vtesdecks.com/collections/users/alice/wishlist"));
        assertFalse(urls.contains("https://vtesdecks.com/collections/users/bob/wishlist"));
        assertTrue(urls.contains("https://vtesdecks.com/collections/users/carol/wishlist"));
        assertFalse(urls.contains("https://vtesdecks.com/collections/users/empty/wishlist"));
        assertTrue(urls.contains("https://vtesdecks.com/collection/binders/public-hash"));
        assertFalse(urls.contains("https://vtesdecks.com/collection/binders/123"));
        assertTrue(urls.contains("https://vtesdecks.com/tutorial/resources"));
        assertFalse(urls.contains("https://vtesdecks.com/vtes-ai"));
        assertEquals(urls.stream().distinct().sorted().toList(), urls);
        assertEquals(published.getModifyDate(), entries.stream().filter(entry -> entry.url().endsWith("/public-deck")).findFirst().orElseThrow().modified());
        assertNull(entries.stream().filter(entry -> entry.url().endsWith("/user/alice")).findFirst().orElseThrow().modified());
        ArgumentCaptor<DeckQuery> query = ArgumentCaptor.forClass(DeckQuery.class);
        verify(decks).getDecks(query.capture());
        assertNull(query.getValue().getType());
        assertNull(query.getValue().getUserId());
        assertFalse(query.getValue().isAllDecks());
        verify(results).close();
    }

    @Test
    void keepsFiftyThousandUrlsInOneDocumentAndSplitsOnlyAboveThat() throws Exception {
        List<SitemapService.Entry> entries = IntStream.range(0, 50_001)
                .mapToObj(index -> new SitemapService.Entry("https://vtesdecks.com/deck/" + index, null)).toList();
        Document index = parse(service.sitemap(entries));
        assertEquals("sitemapindex", index.getDocumentElement().getLocalName());
        assertEquals(2, index.getElementsByTagName("sitemap").getLength());
        assertEquals("https://api.vtesdecks.com/sitemap/2.xml", index.getElementsByTagName("loc").item(1).getTextContent());
        Document first = parse(service.page(entries, 1));
        Document second = parse(service.page(entries, 2));
        assertEquals(50_000, first.getElementsByTagName("url").getLength());
        assertEquals(1, second.getElementsByTagName("url").getLength());
        assertEquals(entries.get(49_999).url(), first.getElementsByTagName("loc").item(49_999).getTextContent());
        assertEquals(entries.get(50_000).url(), second.getElementsByTagName("loc").item(0).getTextContent());
        assertEquals(0, first.getElementsByTagName("lastmod").getLength());
        Document single = parse(service.sitemap(entries.subList(0, 50_000)));
        assertEquals("urlset", single.getDocumentElement().getLocalName());
        assertEquals(50_000, single.getElementsByTagName("url").getLength());
        assertEquals(0, single.getElementsByTagName("sitemap").getLength());
    }

    @Test
    void splitsOnActualUtf8BytesIncludingEscapingDatesAndEnvelope() {
        var entry = new SitemapService.Entry("https://vtesdecks.com/user/é&<name>", LocalDateTime.of(2025, 3, 4, 0, 0));
        var entries = List.of(entry, entry, entry);
        int twoEntryBytes = service.sitemap(entries.subList(0, 2)).getBytes(StandardCharsets.UTF_8).length;
        var exact = service.partition(entries, SitemapService.PAGE_SIZE, twoEntryBytes);
        assertEquals(List.of(2, 1), exact.stream().map(List::size).toList());
        assertEquals(entries, exact.stream().flatMap(List::stream).toList());
        for (var page : exact) {
            assertTrue(service.sitemap(page).getBytes(StandardCharsets.UTF_8).length <= twoEntryBytes);
        }
        assertEquals(List.of(1, 1, 1), service.partition(entries, SitemapService.PAGE_SIZE, twoEntryBytes - 1).stream().map(List::size).toList());
        int oneEntryBytes = service.sitemap(List.of(entry)).getBytes(StandardCharsets.UTF_8).length;
        assertEquals(500, assertThrows(ResponseStatusException.class,
                () -> service.partition(entries, SitemapService.PAGE_SIZE, oneEntryBytes - 1)).getStatusCode().value());
    }

    @Test
    void servesSmallAndEmptyCatalogsAsUrlsets() throws Exception {
        assertEquals("urlset", parse(service.sitemap(List.of())).getDocumentElement().getLocalName());
        var entries = List.of(new SitemapService.Entry("https://vtesdecks.com/", null));
        Document single = parse(service.sitemap(entries));
        assertEquals("urlset", single.getDocumentElement().getLocalName());
        assertEquals(1, single.getElementsByTagName("url").getLength());
    }

    @Test
    void escapesXmlAndOmitsUnknownDates() throws Exception {
        var entries = List.of(
                new SitemapService.Entry("https://vtesdecks.com/user/A&B<\"test\">", null),
                new SitemapService.Entry("https://vtesdecks.com/deck/123", LocalDateTime.of(2025, 3, 4, 12, 30)));
        String xml = service.page(entries, 1);
        Document document = parse(xml);
        assertEquals("http://www.sitemaps.org/schemas/sitemap/0.9", document.getDocumentElement().getNamespaceURI());
        assertEquals(entries.getFirst().url(), document.getElementsByTagName("loc").item(0).getTextContent());
        assertEquals(1, document.getElementsByTagName("lastmod").getLength());
        assertEquals("2025-03-04", document.getElementsByTagName("lastmod").item(0).getTextContent());
    }

    @Test
    void rejectsInvalidPagesIncludingIntegerOverflow() {
        var entries = List.of(new SitemapService.Entry("https://vtesdecks.com/", null));
        for (int page : new int[]{0, -1, 2, Integer.MAX_VALUE}) {
            assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.page(entries, page)).getStatusCode().value());
        }
    }

    @Test
    void doesNotPublishPartialResultsWhenAResourceFails() {
        when(decks.getDecks(any())).thenReturn(results);
        when(results.stream()).thenReturn(Stream.empty());
        when(archetypes.findAll()).thenThrow(new IllegalStateException("Redis unavailable"));
        assertThrows(IllegalStateException.class, () -> service.entries());
        verify(results).close();
        verifyNoInteractions(users, binders);
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }
}
