package com.vtesdecks.api.mapper;

import com.vtesdecks.cache.redis.entity.ArchetypeKeyCard;
import com.vtesdecks.cache.redis.entity.DeckArchetype;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.model.ArchetypeTrend;
import com.vtesdecks.model.MetaType;
import com.vtesdecks.model.api.ApiArchetypeCard;
import com.vtesdecks.model.api.ApiDeckArchetype;
import com.vtesdecks.service.CurrencyExchangeService;
import com.vtesdecks.util.VtesUtils;
import org.mapstruct.AfterMapping;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;

@Mapper(componentModel = "spring")
public abstract class DeckArchetypeMapper {
    @Autowired
    private CurrencyExchangeService currencyExchangeService;

    public abstract List<ApiDeckArchetype> map(List<DeckArchetype> entities, @Context Long metaTotal, @Context MetaType metaType, @Context String currencyCode);

    @Mapping(target = "keyCrypt", ignore = true)
    @Mapping(target = "keyLibrary", ignore = true)
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
            case TOURNAMENT_730:
                api.setMetaCount(entity.getTournament730Count());
                break;
            case TOURNAMENT:
            default:
                api.setMetaCount(entity.getTournamentCount());
        }
        api.setTrend(calculateTrend(entity));
        if (api.getPrice() != null && currencyCode != null && !currencyCode.equalsIgnoreCase(DEFAULT_CURRENCY)) {
            api.setPrice(currencyExchangeService.convert(api.getPrice(), DEFAULT_CURRENCY, currencyCode));
            api.setCurrency(currencyCode);
        }
    }

    /**
     * Calculates the meta trend for an archetype by comparing the per-day tournament rate
     * in the last 90 days vs the rate in days 91–365.
     *
     * <ul>
     *   <li>TRENDING  – recent rate is ≥ 50 % higher than the older rate</li>
     *   <li>DECLINING – recent rate is ≤ 40 % of the older rate</li>
     *   <li>STABLE    – anything in between</li>
     * </ul>
     * <p>
     * Returns {@code null} when there is not enough data (fewer than 3 decks in 365 days).
     */
    private ArchetypeTrend calculateTrend(DeckArchetype entity) {
        Long countLatest = entity.getTournament90Count();
        Long countLongTerm = entity.getTournament365Count();
        if (countLatest == null || countLongTerm == null || countLongTerm < 3) {
            return null;
        }
        // Per-day rates for each period (last 90 days vs days 91-365 = 275 days)
        double rateRecent = countLatest / 90.0;
        long oldCount = countLongTerm - countLatest;
        double rateOld = oldCount / 275.0;

        if (rateOld == 0) {
            // All activity is concentrated in the last 90 days → trending
            return countLatest > 0 ? ArchetypeTrend.TRENDING : null;
        }
        double ratio = rateRecent / rateOld;
        if (ratio >= 1.5) {
            return ArchetypeTrend.TRENDING;
        } else if (ratio <= 0.6) {
            return ArchetypeTrend.DECLINING;
        }
        return ArchetypeTrend.STABLE;
    }

    public List<ApiArchetypeCard> mapKeyCrypt(List<ArchetypeKeyCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        List<ApiArchetypeCard> result = cards.stream()
                .filter(card -> VtesUtils.isCrypt(card.getId()))
                .map(this::toApiArchetypeCard)
                .toList();
        return result.isEmpty() ? null : result;
    }

    public List<ApiArchetypeCard> mapKeyLibrary(List<ArchetypeKeyCard> cards) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        List<ApiArchetypeCard> result = cards.stream()
                .filter(card -> !VtesUtils.isCrypt(card.getId()))
                .map(this::toApiArchetypeCard)
                .toList();
        return result.isEmpty() ? null : result;
    }

    protected abstract ApiArchetypeCard toApiArchetypeCard(ArchetypeKeyCard card);

    public abstract DeckArchetypeEntity map(ApiDeckArchetype api);
}
