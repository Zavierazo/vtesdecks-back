package com.vtesdecks.api.service;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.api.mapper.ApiCardMapper;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.db.CardShopMapper;
import com.vtesdecks.db.model.DbCardShop;
import com.vtesdecks.model.api.ApiCrypt;
import com.vtesdecks.model.api.ApiLibrary;
import com.vtesdecks.model.api.ApiShop;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApiCardService {
    @Autowired
    private CryptCache cryptCache;
    @Autowired
    private LibraryCache libraryCache;
    @Autowired
    private CardShopMapper cardShopMapper;
    @Autowired
    private ApiCardMapper apiCardMapper;

    public ApiCrypt getCrypt(Integer id) {
        Crypt crypt = cryptCache.get(id);
        if (crypt == null) {
            return null;
        }
        return apiCardMapper.mapCrypt(crypt);
    }

    public List<ApiCrypt> getAllCrypt() {
        ResultSet<Crypt> result = cryptCache.selectAll(null, null);
        if (result == null) {
            return Collections.emptyList();
        }
        return result.stream()
                .map(apiCardMapper::mapCrypt)
                .collect(Collectors.toList());
    }

    public ApiCrypt getCryptLastUpdate() {
        Crypt crypt = cryptCache.selectLastUpdated();
        return apiCardMapper.mapCrypt(crypt);
    }

    public ApiLibrary getLibrary(Integer id) {
        Library library = libraryCache.get(id);
        if (library == null) {
            return null;
        }
        return apiCardMapper.mapLibrary(library);
    }

    public List<ApiLibrary> getAllLibrary() {
        ResultSet<Library> result = libraryCache.selectAll(null, null);
        if (result == null) {
            return Collections.emptyList();
        }
        return result.stream()
                .map(apiCardMapper::mapLibrary)
                .collect(Collectors.toList());
    }

    public ApiLibrary getLibraryLastUpdate() {
        Library library = libraryCache.selectLastUpdated();
        return apiCardMapper.mapLibrary(library);
    }

    public List<ApiShop> getCardShops(Integer cardId) {
        List<DbCardShop> cardShopList = cardShopMapper.selectByCardId(cardId);
        return apiCardMapper.mapCardShop(cardShopList);
    }

}
