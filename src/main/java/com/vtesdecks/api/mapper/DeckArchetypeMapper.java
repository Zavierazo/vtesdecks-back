package com.vtesdecks.api.mapper;

import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.model.api.ApiDeckArchetype;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DeckArchetypeMapper {
    ApiDeckArchetype map(DeckArchetypeEntity entity);

    DeckArchetypeEntity map(ApiDeckArchetype api);

    List<ApiDeckArchetype> map(List<DeckArchetypeEntity> entities);
}

