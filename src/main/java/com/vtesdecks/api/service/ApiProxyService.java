package com.vtesdecks.api.service;

import com.itextpdf.text.DocumentException;
import com.vtesdecks.cache.ProxyCardOptionCache;
import com.vtesdecks.cache.SetCache;
import com.vtesdecks.cache.indexable.Set;
import com.vtesdecks.cache.indexable.proxy.ProxyCardOption;
import com.vtesdecks.model.api.ApiProxyCard;
import com.vtesdecks.model.api.ApiProxyCardOption;
import com.vtesdecks.service.ProxyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApiProxyService {

    private static final Set PROMO_SET = Set.builder().fullName("Promo").releaseDate(LocalDate.MIN).build();

    @Autowired
    private ProxyService proxyService;

    @Autowired
    private ProxyCardOptionCache cardOptionCache;

    @Autowired
    private SetCache setCache;

    public byte[] generatePDF(List<ApiProxyCard> cards) throws IOException, DocumentException {
        return proxyService.generatePDF(cards);
    }

    public List<ApiProxyCardOption> getProxyOptions(Integer cardId){
        return cardOptionCache.get(cardId).stream()
                .map(this::map)
                .collect(Collectors.toList());
    }

    private ApiProxyCardOption map(ProxyCardOption proxyCardOption){
        Set set = isPromoSet(proxyCardOption) ? PROMO_SET : setCache.get(proxyCardOption.getSetAbbrev());
        return ApiProxyCardOption.builder()
                .cardId(proxyCardOption.getCardId())
                .setAbbrev(set.getAbbrev())
                .setReleaseDate(set.getReleaseDate())
                .setName(set.getFullName())
                .imageUrl(ProxyService.getImageUrl(set.getAbbrev(), proxyCardOption.getCardId()))
                .build();
    }

    private boolean isPromoSet(ProxyCardOption proxyCardOption){
        return "Promo".equals(proxyCardOption.getSetAbbrev());
    }
}
