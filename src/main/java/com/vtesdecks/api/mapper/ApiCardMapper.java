package com.vtesdecks.api.mapper;

import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.I18n;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.model.ShopPlatform;
import com.vtesdecks.model.api.ApiCrypt;
import com.vtesdecks.model.api.ApiI18n;
import com.vtesdecks.model.api.ApiLibrary;
import com.vtesdecks.model.api.ApiShop;
import com.vtesdecks.model.api.ApiShopInfo;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ApiCardMapper {

    @Mapping(target = "i18n", ignore = true)
    public abstract ApiCrypt mapCrypt(Crypt entity, @Context String locale, @Context Double score);

    @AfterMapping
    protected void afterMapping(@MappingTarget ApiCrypt apiCrypt, Crypt entity, @Context String locale, @Context Double score) {
        if (locale != null && entity.getI18n() != null && entity.getI18n().containsKey(locale)) {
            apiCrypt.setI18n(mapI18n(entity.getI18n().get(locale)));
        }
        apiCrypt.setScore(score);
    }

    @Mapping(target = "i18n", ignore = true)
    public abstract ApiLibrary mapLibrary(Library entity, @Context String locale, @Context Double score);

    @AfterMapping
    protected void afterMapping(@MappingTarget ApiLibrary apiLibrary, Library entity, @Context String locale, @Context Double score) {
        if (locale != null && entity.getI18n() != null && entity.getI18n().containsKey(locale)) {
            apiLibrary.setI18n(mapI18n(entity.getI18n().get(locale)));
        }
        apiLibrary.setScore(score);
    }

    public abstract ApiI18n mapI18n(I18n entity);

    public abstract List<ApiShop> mapCardShop(List<CardShopEntity> entity);

    @Mapping(target = "platform", source = "platform")
    @Mapping(target = "shopInfo", source = "platform")
    public abstract ApiShop mapCardShop(CardShopEntity entity);

    @Mapping(target = "name", source = "platform")
    public abstract ApiShopInfo mapShopInfo(ShopPlatform platform);


}
