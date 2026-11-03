package com.vtesdecks.cache.factory;

import com.vtesdecks.cache.indexable.Card;
import com.vtesdecks.cache.indexable.I18n;
import com.vtesdecks.jpa.entity.CardI18nImageEntity;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CardI18nImageFactory {
    private CardI18nImageFactory() {
    }

    public static void merge(Card card, String originalText, List<CardI18nImageEntity> images) {
        Map<String, I18n> translations = card.getI18n();
        if (translations == null && images.isEmpty()) {
            return;
        }
        if (translations == null) {
            translations = new HashMap<>();
        }
        for (CardI18nImageEntity image : images) {
            I18n translation = translations.computeIfAbsent(image.getId().getLocale(), locale -> new I18n());
            translation.setImage(image.getImage());
            if (image.getModificationDate() != null
                    && (card.getLastUpdate() == null || image.getModificationDate().isAfter(card.getLastUpdate()))) {
                card.setLastUpdate(image.getModificationDate());
            }
        }
        for (I18n translation : translations.values()) {
            translation.setName(StringUtils.defaultIfBlank(translation.getName(), card.getName()));
            translation.setText(StringUtils.defaultIfBlank(translation.getText(), originalText));
        }
        card.setI18n(translations);
    }
}
