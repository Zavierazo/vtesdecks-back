package com.vtesdecks.api.mapper;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.jpa.entity.CardErrataEntity;
import com.vtesdecks.model.api.ApiCardErrata;
import com.vtesdecks.util.VtesUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class ApiCardErrataMapper {
    @Autowired
    private LibraryCache libraryCache;
    @Autowired
    private CryptCache cryptCache;

    @Mapping(target = "id", source = "cardId")
    @Mapping(target = "cardId", source = "cardId")
    @Mapping(target = "name", source = "cardId", qualifiedByName = "mapCardName")
    public abstract ApiCardErrata mapCardErrata(CardErrataEntity cardErrata);

    @Named("mapCardName")
    protected String mapCardName(Integer cardId) {
        if (VtesUtils.isLibrary(cardId)) {
            Library library = libraryCache.get(cardId);
            if (library == null) {
                return null;
            }
            return library.getName();
        } else {
            Crypt crypt = cryptCache.get(cardId);
            if (crypt == null) {
                return null;
            }
            return crypt.getName();
        }
    }
}
