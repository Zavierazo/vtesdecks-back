package com.vtesdecks.api.mapper;

import com.vtesdecks.cache.redis.entity.DeckArchetype;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.model.MetaType;
import com.vtesdecks.model.api.ApiDeckArchetype;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class DeckArchetypeMapper {

    public abstract List<ApiDeckArchetype> map(List<DeckArchetype> entities, @Context Long metaTotal, @Context MetaType metaType);

    public abstract ApiDeckArchetype map(DeckArchetype entity, @Context Long metaTotal, @Context MetaType metaType);

    @AfterMapping
    protected void afterMapping(@MappingTarget ApiDeckArchetype api, DeckArchetype entity, @Context Long metaTotal, @Context MetaType metaType) {
        api.setMetaTotal(metaTotal);
        switch (metaType) {
            case TOURNAMENT_90:
                api.setMetaCount(entity.getTournament90Count());
                break;
            case TOURNAMENT_180:
                api.setMetaCount(entity.getTournament180Count());
                break;
            case TOURNAMENT_365:
                api.setMetaCount(entity.getTournament365Count());
                break;
            case TOURNAMENT:
            default:
                api.setMetaCount(entity.getTournamentCount());
        }
    }

    public abstract ApiDeckArchetype map(DeckArchetypeEntity entity);

    public abstract DeckArchetypeEntity map(ApiDeckArchetype api);
}

