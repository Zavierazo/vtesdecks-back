package com.vtesdecks.cache.factory;

import com.vtesdecks.cache.indexable.Set;
import com.vtesdecks.db.model.DbSet;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public abstract class SetFactory {
    public abstract Set getSet(DbSet dbSet);

    @AfterMapping
    protected void afterMapping(@MappingTarget Set set, DbSet dbSet) {
        set.setLastUpdate(dbSet.getModificationDate() != null ? dbSet.getModificationDate() : dbSet.getCreationDate());
    }
}
