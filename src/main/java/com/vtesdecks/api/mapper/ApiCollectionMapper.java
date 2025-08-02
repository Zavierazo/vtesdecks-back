package com.vtesdecks.api.mapper;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.SetCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.jpa.entities.Collection;
import com.vtesdecks.jpa.entities.CollectionBinder;
import com.vtesdecks.jpa.entities.CollectionCard;
import com.vtesdecks.model.api.ApiCollection;
import com.vtesdecks.model.api.ApiCollectionBinder;
import com.vtesdecks.model.api.ApiCollectionCard;
import com.vtesdecks.model.api.ApiCollectionCardCsv;
import com.vtesdecks.model.api.ApiCollectionPage;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import java.util.List;

import static com.vtesdecks.util.VtesUtils.isLibrary;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public abstract class ApiCollectionMapper {
    @Autowired
    private SetCache setCache;
    @Autowired
    private ApiSetMapper apiSetMapper;
    @Autowired
    private LibraryCache libraryCache;
    @Autowired
    private CryptCache cryptCache;

    public abstract ApiCollection mapCollection(Collection entity, List<CollectionBinder> binders);

    public abstract List<ApiCollectionBinder> mapBinders(List<CollectionBinder> entity);

    public abstract ApiCollectionBinder mapBinder(CollectionBinder entity);

    @Mapping(target = "collectionId", ignore = true)
    public abstract CollectionBinder mapBinderEntity(ApiCollectionBinder entity);

    @Mapping(target = "content", source = "content")
    @Mapping(target = "totalPages", source = "totalPages")
    @Mapping(target = "totalElements", source = "totalElements")
    public abstract ApiCollectionPage<ApiCollectionCard> mapCards(Page<CollectionCard> entity);

    public abstract List<ApiCollectionCard> mapCards(List<CollectionCard> entity);

    @Mapping(target = "cardName", source = "cardId", qualifiedByName = "mapCardName")
    public abstract ApiCollectionCard mapCard(CollectionCard entity);

    @Mapping(target = "collectionId", ignore = true)
    @Mapping(target = "crypt", ignore = true)
    @Mapping(target = "library", ignore = true)
    public abstract CollectionCard mapCardToEntity(ApiCollectionCard entity);

    public abstract List<ApiCollectionCardCsv> mapCsv(List<CollectionCard> entity, @Context List<CollectionBinder> binders);

    @Mapping(target = "cardName", source = "cardId", qualifiedByName = "mapCardName")
    @Mapping(target = "set", source = "set")
    @Mapping(target = "binder", source = "binderId", qualifiedByName = "mapBinder")
    public abstract ApiCollectionCardCsv mapCsv(CollectionCard entity, @Context List<CollectionBinder> binders);


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "collectionId", ignore = true)
    @Mapping(target = "cardId", ignore = true)
    @Mapping(target = "crypt", ignore = true)
    @Mapping(target = "library", ignore = true)
    @Mapping(target = "binderId", ignore = true)
    @Mapping(target = "creationDate", ignore = true)
    @Mapping(target = "modificationDate", ignore = true)
    public abstract CollectionCard mapCsvToEntity(ApiCollectionCardCsv entity);

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
    protected String mapBinder(Integer binderId, @Context List<CollectionBinder> binders) {
        if (binderId == null) {
            return null;
        }
        for (CollectionBinder binder : binders) {
            if (binder.getId().equals(binderId)) {
                return binder.getName();
            }
        }
        return null;
    }
}
