package com.vtesdecks.api.mapper;

import com.vtesdecks.cache.indexable.Set;
import com.vtesdecks.model.api.ApiSet;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class ApiSetMapper {

    public abstract ApiSet map(Set set);
}