package com.vtesdecks.cache.factory;

import com.vtesdecks.cache.indexable.Set;
import com.vtesdecks.jpa.entity.SetEntity;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public abstract class SetFactory {
    public abstract Set getSet(SetEntity dbSet);

    @AfterMapping
    protected void afterMapping(@MappingTarget Set set, SetEntity dbSet) {
        set.setLastUpdate(dbSet.getModificationDate() != null ? dbSet.getModificationDate() : dbSet.getCreationDate());
    }
}
