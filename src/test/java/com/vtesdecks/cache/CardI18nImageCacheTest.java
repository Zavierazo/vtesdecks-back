package com.vtesdecks.cache;

import com.vtesdecks.cache.factory.CryptFactory;
import com.vtesdecks.cache.factory.LibraryFactory;
import com.vtesdecks.api.mapper.ApiCardMapper;
import com.vtesdecks.jpa.entity.CardI18nImageEntity;
import com.vtesdecks.jpa.entity.CardI18nImageEntity.CardI18nImageId;
import com.vtesdecks.jpa.entity.CryptEntity;
import com.vtesdecks.jpa.entity.LibraryEntity;
import com.vtesdecks.jpa.repositories.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardI18nImageCacheTest {
    @Mock private CryptRepository cryptRepository;
    @Mock private CryptI18nRepository cryptI18nRepository;
    @Mock private LibraryRepository libraryRepository;
    @Mock private LibraryI18nRepository libraryI18nRepository;
    @Mock private CardI18nImageRepository imageRepository;
    @Mock private DeckCardRepository deckCardRepository;
    @Mock private CardShopRepository cardShopRepository;
    @Mock private LimitedFormatRepository limitedFormatRepository;

    @Test
    void buildsBothCachesFromBatchedIndependentImagesAndDetectsRemoval() {
        var crypt = new CryptEntity();
        crypt.setId(200001);
        crypt.setName("Original vampire");
        crypt.setType("Vampire");
        crypt.setClan("Toreador");
        crypt.setDisciplines("aus cel pre");
        crypt.setCapacity(5);
        crypt.setGroup(1);
        crypt.setText("Camarilla.");
        crypt.setSet("Jyhad:C");
        crypt.setModificationDate(LocalDateTime.of(2026, 11, 1, 0, 0));
        var library = new LibraryEntity();
        library.setId(100001);
        library.setName("Original weapon");
        library.setType("Equipment");
        library.setText("Weapon.");
        library.setSet("Jyhad:C");
        library.setModificationDate(crypt.getModificationDate());
        when(cryptRepository.findAll()).thenReturn(List.of(crypt));
        when(libraryRepository.findAll()).thenReturn(List.of(library));
        when(imageRepository.findAll()).thenReturn(List.of(image(200001), image(100001)));
        var cryptCache = new CryptCache(cryptRepository, cryptI18nRepository, imageRepository, deckCardRepository,
                cardShopRepository, Mappers.getMapper(CryptFactory.class), limitedFormatRepository);
        var libraryCache = new LibraryCache(libraryRepository, libraryI18nRepository, imageRepository, deckCardRepository,
                cardShopRepository, Mappers.getMapper(LibraryFactory.class), limitedFormatRepository);
        cryptCache.setUp();
        libraryCache.setUp();

        cryptCache.refreshIndex();
        libraryCache.refreshIndex();

        assertNotNull(cryptCache.get(200001));
        assertNotNull(libraryCache.get(100001));
        assertEquals("/img/cards/es/200001.jpg", cryptCache.get(200001).getI18n().get("es").getImage());
        assertEquals("Original vampire", cryptCache.get(200001).getI18n().get("es").getName());
        assertEquals("/img/cards/es/100001.jpg", libraryCache.get(100001).getI18n().get("es").getImage());
        assertEquals("Weapon.", libraryCache.get(100001).getI18n().get("es").getText());
        var mapper = Mappers.getMapper(ApiCardMapper.class);
        var apiCrypt = mapper.mapCrypt(cryptCache.get(200001), "es", null, null);
        var apiLibrary = mapper.mapLibrary(libraryCache.get(100001), "es", null, null);
        assertEquals("/img/cards/es/200001.jpg", apiCrypt.getI18n().getImage());
        assertEquals("Original vampire", apiCrypt.getI18n().getName());
        assertEquals("Camarilla.", apiCrypt.getI18n().getText());
        assertEquals("/img/cards/es/100001.jpg", apiLibrary.getI18n().getImage());
        verify(imageRepository, times(2)).findAll();

        crypt.setModificationDate(LocalDateTime.of(2026, 11, 3, 0, 0));
        when(imageRepository.findAll()).thenReturn(List.of(image(100001)));
        cryptCache.refreshIndex();

        assertNull(cryptCache.get(200001).getI18n());
        assertEquals(crypt.getModificationDate(), cryptCache.get(200001).getLastUpdate());
    }

    private CardI18nImageEntity image(int cardId) {
        var image = new CardI18nImageEntity();
        image.setId(new CardI18nImageId(cardId, "es"));
        image.setImage("/img/cards/es/" + cardId + ".jpg");
        image.setModificationDate(LocalDateTime.of(2026, 11, 2, 0, 0));
        return image;
    }
}
