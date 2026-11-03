package com.vtesdecks.cache.factory;

import com.vtesdecks.cache.indexable.Card;
import com.vtesdecks.cache.indexable.I18n;
import com.vtesdecks.jpa.entity.CardI18nImageEntity;
import com.vtesdecks.jpa.entity.CardI18nImageEntity.CardI18nImageId;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CardI18nImageFactoryTest {
    @Test
    void mergesIndependentImagesAndTranslationsAndPreservesThePublicShape() {
        Card card = card();
        I18n spanish = new I18n();
        spanish.setName("Nombre");
        spanish.setText("Descripción");
        card.setI18n(new HashMap<>(Map.of("es", spanish)));
        CardI18nImageEntity frenchImage = image("fr");

        CardI18nImageFactory.merge(card, "Original text", List.of(frenchImage));

        assertEquals("Nombre", card.getI18n().get("es").getName());
        assertEquals("Descripción", card.getI18n().get("es").getText());
        assertNull(card.getI18n().get("es").getImage());
        assertEquals("Original name", card.getI18n().get("fr").getName());
        assertEquals("Original text", card.getI18n().get("fr").getText());
        assertEquals("/img/cards/fr/200001.jpg", card.getI18n().get("fr").getImage());
        assertEquals(frenchImage.getModificationDate(), card.getLastUpdate());
    }

    @Test
    void removingATranslationKeepsTheImageAndFallsBackToOriginalText() {
        Card card = card();

        CardI18nImageFactory.merge(card, "Original text", List.of(image("es")));

        assertEquals("Original name", card.getI18n().get("es").getName());
        assertEquals("Original text", card.getI18n().get("es").getText());
        assertEquals("/img/cards/es/200001.jpg", card.getI18n().get("es").getImage());
    }

    @Test
    void removalUsesTheTouchedCardDateAndDoesNotKeepTheOldImage() {
        Card card = card();
        card.setLastUpdate(LocalDateTime.of(2026, 11, 3, 4, 0));
        I18n translation = new I18n();
        translation.setName("Nombre");
        card.setI18n(new HashMap<>(Map.of("es", translation)));

        CardI18nImageFactory.merge(card, "Original text", List.of());

        assertNull(card.getI18n().get("es").getImage());
        assertEquals("Original text", card.getI18n().get("es").getText());
        assertEquals(LocalDateTime.of(2026, 11, 3, 4, 0), card.getLastUpdate());
    }

    @Test
    void leavesCardsWithoutTranslationsOrImagesUnlocalized() {
        Card card = card();

        CardI18nImageFactory.merge(card, "Original text", List.of());

        assertNull(card.getI18n());
    }

    private Card card() {
        Card card = new Card();
        card.setId(200001);
        card.setName("Original name");
        card.setLastUpdate(LocalDateTime.of(2026, 11, 1, 0, 0));
        return card;
    }

    private CardI18nImageEntity image(String locale) {
        var image = new CardI18nImageEntity();
        image.setId(new CardI18nImageId(200001, locale));
        image.setImage("/img/cards/" + locale + "/200001.jpg");
        image.setModificationDate(LocalDateTime.of(2026, 11, 2, 0, 0));
        return image;
    }
}
