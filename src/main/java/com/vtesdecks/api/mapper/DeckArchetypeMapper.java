package com.vtesdecks.api.mapper;

import com.vtesdecks.cache.redis.entity.DeckArchetype;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.model.MetaType;
import com.vtesdecks.model.api.ApiDeckArchetype;
import com.vtesdecks.service.CurrencyExchangeService;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;

@Mapper(componentModel = "spring")
public abstract class DeckArchetypeMapper {
    @Autowired
    private CurrencyExchangeService currencyExchangeService;

    public abstract List<ApiDeckArchetype> map(List<DeckArchetype> entities, @Context Long metaTotal, @Context MetaType metaType, @Context String currencyCode);

    public abstract ApiDeckArchetype map(DeckArchetype entity, @Context Long metaTotal, @Context MetaType metaType, @Context String currencyCode);

    @AfterMapping
    protected void afterMapping(@MappingTarget ApiDeckArchetype api, DeckArchetype entity, @Context Long metaTotal, @Context MetaType metaType, @Context String currencyCode) {
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
        if (api.getPrice() != null && currencyCode != null && !currencyCode.equalsIgnoreCase(DEFAULT_CURRENCY)) {
            api.setPrice(currencyExchangeService.convert(api.getPrice(), DEFAULT_CURRENCY, currencyCode));
            api.setCurrency(currencyCode);
        }
    }

    public abstract ApiDeckArchetype map(DeckArchetypeEntity entity);

    public abstract DeckArchetypeEntity map(ApiDeckArchetype api);
}

