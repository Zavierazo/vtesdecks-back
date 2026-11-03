package com.vtesdecks.service;

import com.vtesdecks.jpa.entity.CardI18nImageEntity;
import com.vtesdecks.jpa.entity.CardI18nImageEntity.CardI18nImageId;
import com.vtesdecks.jpa.repositories.CardI18nImageRepository;
import com.vtesdecks.jpa.repositories.CryptRepository;
import com.vtesdecks.jpa.repositories.LibraryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static com.vtesdecks.service.I18nImageClient.Availability.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardI18nImageServiceTest {
    @Mock private CardI18nImageRepository imageRepository;
    @Mock private CryptRepository cryptRepository;
    @Mock private LibraryRepository libraryRepository;
    @InjectMocks private CardI18nImageService service;

    @BeforeEach
    void catalog() {
        when(cryptRepository.selectIds()).thenReturn(List.of(200001));
        when(libraryRepository.selectIds()).thenReturn(List.of(100001));
    }

    @Test
    void insertsImagesWithoutRequiringATranslationAndTouchesOnlyChangedCards() {
        when(imageRepository.findAll()).thenReturn(List.of());

        var changes = service.synchronize(List.of(check(200001, "fr", PRESENT), check(100001, "es", MISSING)));

        assertEquals(new CardI18nImageService.Changes(1, 0, 0, true, false), changes);
        verify(imageRepository).saveAll(argThat(rows -> {
            CardI18nImageEntity row = rows.iterator().next();
            return row.getId().equals(new CardI18nImageId(200001, "fr"))
                    && row.getImage().equals("/img/cards/fr/200001.jpg");
        }));
        verify(cryptRepository).touchModificationDate(Set.of(200001));
        verify(libraryRepository, never()).touchModificationDate(any());
        verify(imageRepository, never()).deleteAllInBatch(any(Iterable.class));
    }

    @Test
    void writesNothingForExistingImagesAndFailedChecks() {
        var present = image(200001, "fr");
        var failed = image(100001, "es");
        when(imageRepository.findAll()).thenReturn(List.of(present, failed));

        assertEquals(new CardI18nImageService.Changes(0, 0, 0, false, false),
                service.synchronize(List.of(check(200001, "fr", PRESENT), check(100001, "es", FAILED))));

        verify(imageRepository, never()).saveAll(any());
        verify(imageRepository, never()).deleteAllInBatch(any(Iterable.class));
        verify(cryptRepository, never()).touchModificationDate(any());
        verify(libraryRepository, never()).touchModificationDate(any());
        assertEquals("/img/cards/es/100001.jpg", failed.getImage());
    }

    @Test
    void removesOnlyConfirmedMissingImagesAndOrphansIncludingUnconfiguredLocales() {
        var missing = image(200001, "fr");
        var retained = image(100001, "es");
        var orphan = image(200999, "de");
        when(imageRepository.findAll()).thenReturn(List.of(missing, retained, orphan));

        var changes = service.synchronize(List.of(check(200001, "fr", MISSING), check(100001, "es", FAILED),
                check(200999, "fr", PRESENT)));

        assertEquals(new CardI18nImageService.Changes(0, 0, 2, true, false), changes);
        verify(imageRepository).deleteAllInBatch(argThat(rows -> {
            Set<CardI18nImageEntity> deleted = new java.util.HashSet<>();
            rows.forEach(deleted::add);
            return deleted.equals(Set.of(missing, orphan));
        }));
        verify(cryptRepository).touchModificationDate(Set.of(200001));
        verify(imageRepository, never()).saveAll(any());
    }

    @Test
    void updatesLegacyImagePathsOnlyWhenTheyDiffer() {
        var legacy = image(100001, "es");
        legacy.setImage("/img/cards/es/old.jpg");
        when(imageRepository.findAll()).thenReturn(List.of(legacy));

        assertEquals(new CardI18nImageService.Changes(0, 1, 0, false, true),
                service.synchronize(List.of(check(100001, "es", PRESENT))));

        verify(imageRepository).saveAll(List.of(legacy));
        verify(libraryRepository).touchModificationDate(Set.of(100001));
        assertEquals("/img/cards/es/100001.jpg", legacy.getImage());
    }

    private I18nImageClient.Check check(int cardId, String locale, I18nImageClient.Availability availability) {
        return new I18nImageClient.Check(new CardI18nImageId(cardId, locale), availability);
    }

    private CardI18nImageEntity image(int cardId, String locale) {
        var image = new CardI18nImageEntity();
        image.setId(new CardI18nImageId(cardId, locale));
        image.setImage(I18nImageClient.imagePath(image.getId()));
        return image;
    }
}
