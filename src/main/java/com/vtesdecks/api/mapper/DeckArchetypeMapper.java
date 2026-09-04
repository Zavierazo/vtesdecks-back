package com.vtesdecks.api.mapper;

import com.vtesdecks.cache.redis.entity.ArchetypeKeyCard;
import com.vtesdecks.cache.redis.entity.DeckArchetype;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.model.ArchetypeTrend;
import com.vtesdecks.model.ArchetypeMetaMetrics;
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
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.vtesdecks.util.Constants.DEFAULT_CURRENCY;

@Mapper(componentModel = "spring")
public abstract class DeckArchetypeMapper {
    @Autowired
    private CurrencyExchangeService currencyExchangeService;
    @Autowired
    private CryptCache cryptCache;
    @Autowired
    private LibraryCache libraryCache;

    public abstract List<ApiDeckArchetype> map(List<DeckArchetype> entities, @Context Map<Integer, ArchetypeMetaMetrics> metrics, @Context MetaType metaType, @Context String currencyCode);

    @Mapping(target = "keyCrypt", ignore = true)
    @Mapping(target = "keyLibrary", ignore = true)
    public abstract ApiDeckArchetype map(DeckArchetype entity, @Context Map<Integer, ArchetypeMetaMetrics> metrics, @Context MetaType metaType, @Context String currencyCode);

    @AfterMapping
    protected void afterMapping(@MappingTarget ApiDeckArchetype api, DeckArchetype entity, @Context Map<Integer, ArchetypeMetaMetrics> metrics, @Context MetaType metaType, @Context String currencyCode) {
        ArchetypeMetaMetrics period = metrics.get(entity.getId());
        if (period != null) {
            api.setMetaCount(period.currentCount());
            api.setMetaTotal(period.currentTotal());
            api.setPreviousMetaCount(period.previousCount());
            api.setPreviousMetaTotal(period.previousTotal());
            api.setMetaShareChange(calculateShareChange(period));
            api.setTrend(calculateTrend(period, metaType));
        }
        populateProfile(api, entity);
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
     * Returns {@code null} for all-time data or when the current and previous periods
     * contain fewer than three matching decks in total.
     */
    private ArchetypeTrend calculateTrend(ArchetypeMetaMetrics metrics, MetaType metaType) {
        if (metaType == MetaType.TOURNAMENT || metrics.previousCount() == null || metrics.previousTotal() == null) {
            return null;
        }
        if (metrics.currentCount() + metrics.previousCount() < 3) {
            return null;
        }
        if (metrics.previousCount() == 0) {
            return metrics.currentCount() >= 3 ? ArchetypeTrend.TRENDING : null;
        }
        if (metrics.currentTotal() == 0 || metrics.previousTotal() == 0) {
            return null;
        }
        double currentShare = metrics.currentCount() / (double) metrics.currentTotal();
        double previousShare = metrics.previousCount() / (double) metrics.previousTotal();
        double ratio = currentShare / previousShare;
        if (ratio >= 1.5) {
            return ArchetypeTrend.TRENDING;
        } else if (ratio <= 0.5) {
            return ArchetypeTrend.DECLINING;
        }
        return ArchetypeTrend.STABLE;
    }

    private void populateProfile(ApiDeckArchetype api, DeckArchetype entity) {
        Set<String> clans = entity.getClans() == null
                ? new TreeSet<>()
                : new TreeSet<>(entity.getClans());
        Set<String> disciplines = entity.getDisciplines() == null
                ? new TreeSet<>()
                : new TreeSet<>(entity.getDisciplines());
        if (entity.getKeyCards() != null) {
            entity.getKeyCards().stream()
                    .filter(card -> card.getAppearanceRate() != null && card.getAppearanceRate() >= 0.5)
                    .map(ArchetypeKeyCard::getId)
                    .map(cryptCache::get)
                    .filter(java.util.Objects::nonNull)
                    .map(crypt -> crypt.getClan())
                    .filter(clan -> clan != null && !clan.isBlank())
                    .forEach(clans::add);
            if (entity.getDisciplines() == null) {
                entity.getKeyCards().stream()
                        .filter(card -> card.getAppearanceRate() != null && card.getAppearanceRate() >= 0.5)
                        .map(ArchetypeKeyCard::getId)
                        .map(libraryCache::get)
                        .filter(java.util.Objects::nonNull)
                        .filter(library -> library.getDisciplines() != null)
                        .forEach(library -> disciplines.addAll(library.getDisciplines()));
            }
        }
        api.setClans(clans);
        api.setDisciplines(disciplines);
    }

    private Double calculateShareChange(ArchetypeMetaMetrics metrics) {
        if (metrics.previousCount() == null || metrics.previousTotal() == null
                || metrics.currentTotal() == 0 || metrics.previousTotal() == 0) {
            return null;
        }
        double currentShare = metrics.currentCount() * 100.0 / metrics.currentTotal();
        double previousShare = metrics.previousCount() * 100.0 / metrics.previousTotal();
        return currentShare - previousShare;
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
