package com.vtesdecks.api.service;

import com.itextpdf.text.DocumentException;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.SetCache;
import com.vtesdecks.cache.indexable.Card;
import com.vtesdecks.cache.indexable.I18n;
import com.vtesdecks.cache.indexable.Set;
import com.vtesdecks.cache.redis.entity.ProxyCardOption;
import com.vtesdecks.cache.redis.repositories.ProxyCardOptionRepository;
import com.vtesdecks.model.api.ApiProxyCard;
import com.vtesdecks.model.api.ApiProxyCardOption;
import com.vtesdecks.scheduler.ProxyCardOptionScheduler;
import com.vtesdecks.service.ProxyService;
import com.vtesdecks.util.VtesUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiProxyService {
    private static final String CDN_BASE_URL = "https://cdn.vtesdecks.com";
    private static final String DEFAULT_LOCALE = "en";

    private final ProxyService proxyService;
    private final ProxyCardOptionRepository proxyCardOptionRepository;
    private final ProxyCardOptionScheduler proxyCardOptionScheduler;
    private final SetCache setCache;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;

    public byte[] generatePDF(List<ApiProxyCard> cards, String locale) throws IOException, DocumentException {
        Map<Integer, List<ApiProxyCardOption>> cardOptions = new HashMap<>();
        Map<Integer, String> languageImages = new HashMap<>();
        for (ApiProxyCard card : cards) {
            List<ApiProxyCardOption> options = getProxyOptions(List.of(card.getCardId()));
            if (options != null) {
                cardOptions.put(card.getCardId(), options);
            }
            if (StringUtils.isBlank(card.getSetAbbrev())) {
                String languageImageUrl = resolveLanguageImageUrl(card.getCardId(), locale);
                if (languageImageUrl != null) {
                    languageImages.put(card.getCardId(), languageImageUrl);
                }
            }
        }
        return proxyService.generatePDF(cards, cardOptions, languageImages);
    }

    private String resolveLanguageImageUrl(Integer cardId, String locale) {
        if (StringUtils.isBlank(locale) || DEFAULT_LOCALE.equalsIgnoreCase(locale)) {
            return null;
        }
        Card card = VtesUtils.isLibrary(cardId) ? libraryCache.get(cardId) : cryptCache.get(cardId);
        if (card == null || card.getI18n() == null) {
            return null;
        }
        I18n i18n = card.getI18n().get(locale);
        if (i18n == null || StringUtils.isBlank(i18n.getImage())) {
            return null;
        }
        return CDN_BASE_URL + i18n.getImage();
    }

    public ApiProxyCardOption getProxyOption(Integer cardId, String set) {
        return proxyCardOptionRepository.findById(cardId)
                .filter(proxyCardOption -> proxyCardOption.getSets().contains(set))
                .map(proxyCardOption -> map(proxyCardOption, set))
                .orElse(null);
    }

    public List<ApiProxyCardOption> getProxyOptions(List<Integer> cardId) {
        Iterable<ProxyCardOption> proxyCardOptions = proxyCardOptionRepository.findAllById(cardId);
        List<ApiProxyCardOption> resultList = new ArrayList<>();
        for (ProxyCardOption proxyCardOption : proxyCardOptions) {
            resultList.addAll(getProxyOptions(proxyCardOption));
        }
        return resultList;
    }

    public List<ApiProxyCardOption> getProxyOptions(ProxyCardOption proxyCardOption) {
        return proxyCardOption.getSets()
                .stream()
                .map(set -> map(proxyCardOption, set))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ApiProxyCardOption::getSetReleaseDate, Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .toList();
    }

    public List<ApiProxyCardOption> getMissingProxyOptions() {
        return proxyCardOptionScheduler.getAllPossibleOptions()
                .flatMap(option -> option.getSets().stream()
                        .filter(set -> getProxyOption(option.getCardId(), set) == null)
                        .map(set -> map(option, set))
                )
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ApiProxyCardOption::getCardId))
                .toList();
    }

    private ApiProxyCardOption map(ProxyCardOption proxyCardOption, String setAbbrev) {
        Set set = setCache.get(setAbbrev);

        if (set == null) {
            log.warn("Set '{}' not found for proxy option", setAbbrev);
            return null;
        }
        return ApiProxyCardOption.builder()
                .cardId(proxyCardOption.getCardId())
                .cardName(proxyCardOption.getCardName())
                .setAbbrev(set.getAbbrev())
                .setReleaseDate(set.getReleaseDate())
                .setName(set.getFullName())
                .imageUrl(proxyService.getProxyImageUrl(set.getAbbrev(), proxyCardOption.getCardId()))
                .build();
    }
}
