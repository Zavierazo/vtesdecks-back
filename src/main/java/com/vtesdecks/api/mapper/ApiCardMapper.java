package com.vtesdecks.api.mapper;

import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.db.model.DbCardShop;
import com.vtesdecks.model.api.ApiCrypt;
import com.vtesdecks.model.api.ApiLibrary;
import com.vtesdecks.model.api.ApiShop;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ApiCardMapper {

    public abstract ApiCrypt mapCrypt(Crypt entity);

    public abstract ApiLibrary mapLibrary(Library entity);

    public abstract List<ApiShop> mapCardShop(List<DbCardShop> entity);

}
