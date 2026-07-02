package com.vtesdecks.api.mapper;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.jpa.entity.WishlistCardEntity;
import com.vtesdecks.model.api.ApiWishlistCard;
import com.vtesdecks.model.api.ApiWishlistPage;
import com.vtesdecks.service.CurrencyExchangeService;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;
import static com.vtesdecks.util.VtesUtils.isLibrary;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class ApiWishlistMapper {
    @Autowired
    private LibraryCache libraryCache;
    @Autowired
    private CryptCache cryptCache;
    @Autowired
    private CurrencyExchangeService currencyExchangeService;

    @Mapping(target = "content", source = "content")
    @Mapping(target = "totalPages", source = "totalPages")
    @Mapping(target = "totalElements", source = "totalElements")
    @Mapping(target = "publicVisibility", ignore = true)
    public abstract ApiWishlistPage<ApiWishlistCard> mapWishlistPage(Page<WishlistCardEntity> entity, @Context String currencyCode);

    @Mapping(target = "cardName", source = "cardId", qualifiedByName = "mapCardName")
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "currency", ignore = true)
    public abstract ApiWishlistCard mapWishlistCard(WishlistCardEntity entity, @Context String currencyCode);

    @AfterMapping
    protected void afterWishlistMapping(@MappingTarget ApiWishlistCard api, @Context String currencyCode) {
        if (currencyCode != null) {
            BigDecimal price = getPrice(api.getCardId());
            if (price != null) {
                api.setPrice(currencyExchangeService.convert(price, DEFAULT_CURRENCY, currencyCode));
                if (api.getNumber() != null && api.getNumber() > 0) {
                    api.setTotalPrice(api.getPrice().multiply(BigDecimal.valueOf(api.getNumber())));
                }
                api.setCurrency(currencyCode);
            }
        }
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "crypt", ignore = true)
    @Mapping(target = "library", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "modificationDate", ignore = true)
    public abstract WishlistCardEntity mapWishlistCardToEntity(ApiWishlistCard entity);

    protected BigDecimal getPrice(Integer cardId) {
        if (cardId == null) {
            return null;
        }
        if (isLibrary(cardId)) {
            return libraryCache.get(cardId).getMinPrice();
        } else {
            return cryptCache.get(cardId).getMinPrice();
        }
    }

    @Named("mapCardName")
    protected String mapCardName(Integer cardId) {
        if (cardId == null) {
            return null;
        }
        if (isLibrary(cardId)) {
            return libraryCache.get(cardId).getName();
        } else {
            Crypt crypt = cryptCache.get(cardId);
            if (crypt.isAdv()) {
                return crypt.getName() + " (ADV)";
            } else {
                return crypt.getName();
            }
        }
    }
}
