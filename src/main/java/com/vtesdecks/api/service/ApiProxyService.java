package com.vtesdecks.api.service;

import com.itextpdf.text.DocumentException;
import com.vtesdecks.cache.ProxyCardOptionCache;
import com.vtesdecks.cache.SetCache;
import com.vtesdecks.cache.indexable.Set;
import com.vtesdecks.cache.indexable.proxy.ProxyCardOption;
import com.vtesdecks.model.api.ApiProxyCard;
import com.vtesdecks.model.api.ApiProxyCardOption;
import com.vtesdecks.service.ProxyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiProxyService {

    private static final Set PROMO_SET = Set.builder().abbrev("Promo").fullName("Promo").releaseDate(LocalDate.MIN).build();
    private static final Set POD_SET = Set.builder().abbrev("POD").fullName("Print On Demand").releaseDate(LocalDate.MIN).build();

    private final ProxyService proxyService;
    private final ProxyCardOptionCache cardOptionCache;
    private final SetCache setCache;

    public byte[] generatePDF(List<ApiProxyCard> cards) throws IOException, DocumentException {
        Map<Integer, List<ApiProxyCardOption>> cardOptions = new HashMap<>();
        for (ApiProxyCard card : cards) {
            List<ApiProxyCardOption> options = getProxyOptions(card.getCardId());
            if (options != null) {
                cardOptions.put(card.getCardId(), options);
            }
        }
        return proxyService.generatePDF(cards, cardOptions);
    }

    public List<ApiProxyCardOption> getProxyOptions(Integer cardId) {
        return cardOptionCache.get(cardId).stream()
                .map(this::map)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(ApiProxyCardOption::getSetReleaseDate).reversed())
                .toList();
    }

    private ApiProxyCardOption map(ProxyCardOption proxyCardOption) {
        Set set;
        if (isPodSet(proxyCardOption)) {
            set = POD_SET;
        } else if (isPromoSet(proxyCardOption)) {
            set = PROMO_SET;
        } else {
            set = setCache.get(proxyCardOption.getSetAbbrev());
        }
        if (set == null) {
            log.warn("Set '{}' not found for proxy option", proxyCardOption.getSetAbbrev());
            return null;
        }
        return ApiProxyCardOption.builder()
                .cardId(proxyCardOption.getCardId())
                .setAbbrev(set.getAbbrev())
                .setReleaseDate(set.getReleaseDate())
                .setName(set.getFullName())
                .imageUrl(proxyService.getProxyImageUrl(set.getAbbrev(), proxyCardOption.getCardId()))
                .build();
    }

    private boolean isPodSet(ProxyCardOption proxyCardOption) {
        return "POD".equals(proxyCardOption.getSetAbbrev());
    }

    private boolean isPromoSet(ProxyCardOption proxyCardOption) {
        return "Promo".equals(proxyCardOption.getSetAbbrev());
    }
}
