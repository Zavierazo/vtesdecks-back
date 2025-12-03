package com.vtesdecks.cache.factory;


import com.google.common.collect.ImmutableList;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.I18n;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.entity.CryptEntity;
import com.vtesdecks.jpa.entity.CryptI18nEntity;
import com.vtesdecks.model.CryptTaint;
import com.vtesdecks.service.CurrencyExchangeService;
import com.vtesdecks.util.VtesUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;

@Mapper(componentModel = "spring")
public abstract class CryptFactory {
    @Autowired
    private CurrencyExchangeService currencyExchangeService;

    @Mapping(target = "disciplines", ignore = true)
    @Mapping(target = "path", source = "dbCrypt.path", qualifiedByName = "mapNonEmpty")
    public abstract Crypt getCrypt(CryptEntity dbCrypt, @Context List<CryptI18nEntity> cryptI18nList, @Context List<CardShopEntity> cardShopList);

    @AfterMapping
    protected void afterMapping(@MappingTarget Crypt crypt, CryptEntity dbCrypt, @Context List<CryptI18nEntity> cryptI18nList, @Context List<CardShopEntity> cardShopList) {
        crypt.setImage("/img/cards/" + dbCrypt.getId() + ".jpg");
        crypt.setCropImage("/img/cards/crop/" + dbCrypt.getId() + ".jpg");
        crypt.setClanIcon(VtesUtils.getClanIcon(dbCrypt.getClan()));
        crypt.setPathIcon(VtesUtils.getPathIcon(dbCrypt.getPath()));
        crypt.setDisciplines(VtesUtils.getCryptDisciplineNames(dbCrypt.getType(), dbCrypt.getDisciplines(), false));
        crypt.setSuperiorDisciplines(VtesUtils.getCryptDisciplineNames(dbCrypt.getType(), dbCrypt.getDisciplines(), true));
        crypt.setDisciplineIcons(VtesUtils.getCryptDisciplines(dbCrypt.getType(), dbCrypt.getDisciplines()));
        crypt.setSect(VtesUtils.getCryptSect(dbCrypt.getType(), dbCrypt.getText()));
        crypt.setTaints(VtesUtils.getCryptTaints(dbCrypt).stream().map(CryptTaint::getName).collect(Collectors.toSet()));
        crypt.setSets(VtesUtils.getSets(dbCrypt.getSet()));
        crypt.setLastUpdate(dbCrypt.getModificationDate() != null ? dbCrypt.getModificationDate() : dbCrypt.getCreationDate());
        crypt.setPrintOnDemand(VtesUtils.isPrintOnDemand(cardShopList));
        if (!CollectionUtils.isEmpty(cardShopList)) {
            // Add all sets from print on demand shops
            if (crypt.isPrintOnDemand()) {
                for (CardShopEntity cardShop : cardShopList) {
                    if (VtesUtils.isPrintOnDemand(cardShopList, cardShop.getPlatform()) && !crypt.getSets().contains(cardShop.getSet())) {
                        crypt.setSets(new ImmutableList.Builder<String>().addAll(crypt.getSets()).add(cardShop.getSet()).build());
                    }
                }
            }
            // Find min and max price in EUR
            List<BigDecimal> priceList = cardShopList.stream()
                    .filter(cardShop -> cardShop.getPlatform().isEnabled() && cardShop.getPrice() != null)
                    .map(cardShop -> currencyExchangeService.convert(cardShop.getPrice(), cardShop.getCurrency(), DEFAULT_CURRENCY))
                    .toList();
            crypt.setMinPrice(priceList.stream().min(BigDecimal::compareTo).orElse(null));
            crypt.setMaxPrice(priceList.stream().max(BigDecimal::compareTo).orElse(null));
            // Force lastUpdate when new shop find
            LocalDateTime cardShopCreationDate = cardShopList.stream()
                    .filter(cardShop -> cardShop.getPlatform().isPrintOnDemand())
                    .map(CardShopEntity::getCreationDate)
                    .findAny()
                    .orElse(null);
            if (cardShopCreationDate != null && cardShopCreationDate.isAfter(crypt.getLastUpdate())) {
                crypt.setLastUpdate(cardShopCreationDate);
            }
        }
        //Crypt i18n
        if (!CollectionUtils.isEmpty(cryptI18nList)) {
            Map<String, I18n> i18nMap = new HashMap<>();
            for (CryptI18nEntity cryptI18n : cryptI18nList) {
                I18n i18n = new I18n();
                i18n.setName(cryptI18n.getName());
                i18n.setText(cryptI18n.getText());
                i18n.setImage(cryptI18n.getImage());
                i18nMap.put(cryptI18n.getId().getLocale(), i18n);
                if (cryptI18n.getModificationDate() != null && cryptI18n.getModificationDate().isAfter(crypt.getLastUpdate())) {
                    crypt.setLastUpdate(cryptI18n.getModificationDate());
                }
            }
            crypt.setI18n(i18nMap);
        }
    }

    @Named("mapNonEmpty")
    public String mapNonEmpty(String value) {
        if (StringUtils.isNotEmpty(value)) {
            return value;
        }
        return null;
    }
}
