package com.vtesdecks.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.api.mapper.ApiCardMapper;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.integration.ScannerClient;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import com.vtesdecks.model.ShopPlatform;
import com.vtesdecks.model.api.ApiShop;
import com.vtesdecks.model.api.ApiShopResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiCardShopServiceTest {
    private final CardShopRepository repository = mock(CardShopRepository.class);
    private final ApiCardService service = new ApiCardService(mock(CryptCache.class), mock(LibraryCache.class),
            repository, Mappers.getMapper(ApiCardMapper.class), mock(ScannerClient.class));

    @Test
    void startsWithTheCheapestEbayOfferAndExpandsToAllSellersAndPrintings() {
        CardShopEntity first = offer(1, ShopPlatform.EBAY, "Jyhad", "first");
        first.setCountry("FR");
        first.setContinent("EU");
        CardShopEntity second = offer(2, ShopPlatform.EBAY, "Jyhad", "second");
        CardShopEntity otherPrinting = offer(3, ShopPlatform.EBAY, "VTES", "first");
        when(repository.findByCardId(100545)).thenReturn(List.of(first, second, otherPrinting));

        ApiShopResult result = service.getCardShops(100545, "en", false);
        ApiShopResult all = service.getCardShops(100545, "en", true);

        assertEquals(1, result.getShops().size());
        assertTrue(result.getHasMore());
        assertEquals(3, all.getShops().size());
        assertFalse(all.getHasMore());
        ApiShop mapped = result.getShops().getFirst();
        assertEquals("first", mapped.getSeller());
        assertEquals("FR", mapped.getCountry());
        assertEquals("EU", mapped.getContinent());
        assertEquals(first.getLink(), mapped.getLink());
    }

    @Test
    void retainsTheExistingGenericPrintingAndPreferredLanguageSummaryForEbay() {
        CardShopEntity cheap = offer(1, ShopPlatform.EBAY, "Jyhad", "first");
        cheap.setLocale("es");
        CardShopEntity unknownPrinting = offer(2, ShopPlatform.EBAY, null, "second");
        when(repository.findByCardId(100545)).thenReturn(List.of(unknownPrinting, cheap));

        ApiShopResult result = service.getCardShops(100545, "en", false);

        assertEquals(1, result.getShops().size());
        assertEquals(unknownPrinting.getLink(), result.getShops().getFirst().getLink());
        assertTrue(result.getHasMore());

        ApiShopResult spanish = service.getCardShops(100545, "es", false);
        assertEquals(2, spanish.getShops().size());
        assertTrue(spanish.getShops().stream().anyMatch(shop -> shop.getLink().equals(cheap.getLink())));
        assertTrue(spanish.getShops().stream().anyMatch(shop -> shop.getLink().equals(unknownPrinting.getLink())));
        assertFalse(spanish.getHasMore());
    }

    @Test
    void keepsOtherPlatformsCompactUntilAllOffersAreRequested() {
        CardShopEntity ebay = offer(1, ShopPlatform.EBAY, "Jyhad", "first");
        CardShopEntity cheap = offer(2, ShopPlatform.TCG_MKT, "Jyhad", null);
        CardShopEntity other = offer(3, ShopPlatform.TCG_MKT, "VTES", null);
        when(repository.findByCardId(100545)).thenReturn(List.of(ebay, cheap, other));

        ApiShopResult initial = service.getCardShops(100545, "en", false);
        ApiShopResult all = service.getCardShops(100545, "en", true);

        assertEquals(2, initial.getShops().size());
        assertTrue(initial.getHasMore());
        assertEquals(3, all.getShops().size());
        assertFalse(all.getHasMore());
    }

    @Test
    void retainsStockFilteringForEbayOffers() {
        CardShopEntity available = offer(1, ShopPlatform.EBAY, "Jyhad", "first");
        CardShopEntity unavailable = offer(2, ShopPlatform.EBAY, "Jyhad", "second");
        unavailable.setInStock(false);
        when(repository.findByCardId(100545)).thenReturn(List.of(available, unavailable));

        assertEquals(1, service.getCardShops(100545, "en", false).getShops().size());
    }

    @Test
    void omitsAbsentMetadataFromThePublicJsonContract() throws Exception {
        when(repository.findByCardId(100545)).thenReturn(List.of(offer(1, ShopPlatform.EBAY, null, null)));

        ApiShop shop = service.getCardShops(100545, "en", false).getShops().getFirst();
        var json = new ObjectMapper().valueToTree(shop);

        assertFalse(json.has("seller"));
        assertFalse(json.has("country"));
        assertFalse(json.has("continent"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"tcgmarket", "TCGMARKET"})
    void omitsOnlyTheHiddenSellerFromResponsesWithoutChangingItsStoredIdentity(String seller) {
        CardShopEntity current = offer(1, ShopPlatform.TCG_MKT, "Jyhad", seller);
        when(repository.findByCardId(100545)).thenReturn(List.of(current));

        for (boolean showAll : new boolean[]{false, true}) {
            ApiShop shop = service.getCardShops(100545, "en", showAll).getShops().getFirst();
            assertNull(shop.getSeller());
            assertFalse(new ObjectMapper().valueToTree(shop).has("seller"));
        }
        assertEquals(seller, current.getSeller());
    }

    @ParameterizedTest
    @ValueSource(strings = {"gamepod", "drivethrucards", "tcgmarket-store", "tcgmarket2", "Example Seller"})
    void keepsEveryOtherSellerName(String seller) {
        when(repository.findByCardId(100545)).thenReturn(List.of(offer(1, ShopPlatform.TCG_MKT, "Jyhad", seller)));

        assertEquals(seller, service.getCardShops(100545, "en", false).getShops().getFirst().getSeller());
    }

    private CardShopEntity offer(int id, ShopPlatform platform, String set, String seller) {
        return CardShopEntity.builder().id(id).cardId(100545).platform(platform).set(set).seller(seller)
                .locale("en").link("https://example.com/" + id).price(BigDecimal.valueOf(id))
                .currency("EUR").inStock(true).build();
    }
}
