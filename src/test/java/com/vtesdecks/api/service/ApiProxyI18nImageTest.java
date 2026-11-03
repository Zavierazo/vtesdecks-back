package com.vtesdecks.api.service;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.SetCache;
import com.vtesdecks.cache.factory.CardI18nImageFactory;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.redis.repositories.ProxyCardOptionRepository;
import com.vtesdecks.jpa.entity.CardI18nImageEntity;
import com.vtesdecks.jpa.entity.CardI18nImageEntity.CardI18nImageId;
import com.vtesdecks.model.api.ApiProxyCard;
import com.vtesdecks.scheduler.ProxyCardOptionScheduler;
import com.vtesdecks.service.ProxyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiProxyI18nImageTest {
    @Mock private ProxyService proxyService;
    @Mock private ProxyCardOptionRepository proxyCardOptionRepository;
    @Mock private ProxyCardOptionScheduler proxyCardOptionScheduler;
    @Mock private SetCache setCache;
    @Mock private CryptCache cryptCache;
    @Mock private LibraryCache libraryCache;
    @InjectMocks private ApiProxyService service;

    @Test
    void passesImagesWithoutTextTranslationsToThePdfGenerator() throws Exception {
        var card = new Crypt();
        card.setId(200001);
        card.setName("Original vampire");
        var image = new CardI18nImageEntity();
        image.setId(new CardI18nImageId(200001, "es"));
        image.setImage("/img/cards/es/200001.jpg");
        CardI18nImageFactory.merge(card, "Original text", List.of(image));
        when(cryptCache.get(200001)).thenReturn(card);
        when(proxyCardOptionRepository.findAllById(List.of(200001))).thenReturn(List.of());
        var cards = List.of(new ApiProxyCard(200001, null, 1));

        service.generatePDF(cards, "es");

        verify(proxyService).generatePDF(eq(cards), anyMap(),
                eq(Map.of(200001, "https://cdn.vtesdecks.com/img/cards/es/200001.jpg")));
    }

    @Test
    void keepsExplicitPrintingsAheadOfTranslatedImages() throws Exception {
        when(proxyCardOptionRepository.findAllById(List.of(200001))).thenReturn(List.of());
        var cards = List.of(new ApiProxyCard(200001, "Jyhad", 1));

        service.generatePDF(cards, "es");

        verify(proxyService).generatePDF(eq(cards), anyMap(), eq(Map.of()));
        verifyNoInteractions(cryptCache, libraryCache);
    }
}
