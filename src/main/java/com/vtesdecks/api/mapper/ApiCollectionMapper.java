package com.vtesdecks.api.mapper;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.jpa.entity.CollectionBinderEntity;
import com.vtesdecks.jpa.entity.CollectionCardEntity;
import com.vtesdecks.jpa.entity.CollectionEntity;
import com.vtesdecks.model.api.ApiCollection;
import com.vtesdecks.model.api.ApiCollectionBinder;
import com.vtesdecks.model.api.ApiCollectionCard;
import com.vtesdecks.model.api.ApiCollectionCardCsv;
import com.vtesdecks.model.api.ApiCollectionPage;
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
import java.util.List;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;
import static com.vtesdecks.util.VtesUtils.isLibrary;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class ApiCollectionMapper {
    @Autowired
    private LibraryCache libraryCache;
    @Autowired
    private CryptCache cryptCache;
    @Autowired
    private CurrencyExchangeService currencyExchangeService;

    public abstract ApiCollection mapCollection(CollectionEntity entity, List<CollectionBinderEntity> binders);

    public abstract List<ApiCollectionBinder> mapBinders(List<CollectionBinderEntity> entity);

    public abstract ApiCollectionBinder mapBinder(CollectionBinderEntity entity);

    @Mapping(target = "collectionId", ignore = true)
    public abstract CollectionBinderEntity mapBinderEntity(ApiCollectionBinder entity);

    @Mapping(target = "content", source = "content")
    @Mapping(target = "totalPages", source = "totalPages")
    @Mapping(target = "totalElements", source = "totalElements")
    public abstract ApiCollectionPage<ApiCollectionCard> mapCards(Page<CollectionCardEntity> entity, @Context String currencyCode);

    public abstract List<ApiCollectionCard> mapCards(List<CollectionCardEntity> entity, @Context String currencyCode);

    @Mapping(target = "cardName", source = "cardId", qualifiedByName = "mapCardName")
    @Mapping(target = "price", ignore = true)
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "currency", ignore = true)
    public abstract ApiCollectionCard mapCard(CollectionCardEntity entity, @Context String currencyCode);

    @AfterMapping
    protected void afterMapping(@MappingTarget ApiCollectionCard api, @Context String currencyCode) {
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

    @Mapping(target = "collectionId", ignore = true)
    @Mapping(target = "crypt", ignore = true)
    @Mapping(target = "library", ignore = true)
    public abstract CollectionCardEntity mapCardToEntity(ApiCollectionCard entity);

    public abstract List<ApiCollectionCardCsv> mapCsv(List<CollectionCardEntity> entity, @Context List<CollectionBinderEntity> binders);

    @Mapping(target = "cardName", source = "cardId", qualifiedByName = "mapCardName")
    @Mapping(target = "set", source = "set")
    @Mapping(target = "binder", source = "binderId", qualifiedByName = "mapBinder")
    public abstract ApiCollectionCardCsv mapCsv(CollectionCardEntity entity, @Context List<CollectionBinderEntity> binders);


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "collectionId", ignore = true)
    @Mapping(target = "cardId", ignore = true)
    @Mapping(target = "crypt", ignore = true)
    @Mapping(target = "library", ignore = true)
    @Mapping(target = "binderId", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "modificationDate", ignore = true)
    public abstract CollectionCardEntity mapCsvToEntity(ApiCollectionCardCsv entity);

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

    @Named("mapBinder")
    protected String mapBinder(Integer binderId, @Context List<CollectionBinderEntity> binders) {
        if (binderId == null) {
            return null;
        }
        for (CollectionBinderEntity binder : binders) {
            if (binder.getId().equals(binderId)) {
                return binder.getName();
            }
        }
        return null;
    }
}
