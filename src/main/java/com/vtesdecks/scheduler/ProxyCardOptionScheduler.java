package com.vtesdecks.scheduler;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.redis.entity.ProxyCardOption;
import com.vtesdecks.cache.redis.repositories.ProxyCardOptionRepository;
import com.vtesdecks.jpa.entity.CryptEntity;
import com.vtesdecks.jpa.entity.LibraryEntity;
import com.vtesdecks.jpa.repositories.CryptRepository;
import com.vtesdecks.jpa.repositories.LibraryRepository;
import com.vtesdecks.service.ProxyService;
import com.vtesdecks.util.VtesUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProxyCardOptionScheduler {
    private final ProxyCardOptionRepository proxyCardOptionRepository;
    private final LibraryCache libraryCache;
    private final CryptCache cryptCache;
    private final ProxyService proxyService;
    private final CryptRepository cryptRepository;
    private final LibraryRepository libraryRepository;

    private record PromoCheck(boolean promoImageExists, boolean pfaImageExists) {
    }

    @Transactional
    @Scheduled(cron = "${jobs.proxyCardOptionCron:0 0 3 * * *}")
    public void proxyCardOptionScheduler() {
        log.info("Starting ProxyCardOption cache refresh...");
        try {
            // Get existing entries
            Set<Integer> existingCardIds = new HashSet<>();
            proxyCardOptionRepository.findAll().forEach(proxyCardOption -> existingCardIds.add(proxyCardOption.getCardId()));

            // Check all possible options in parallel
            Map<Integer, PromoCheck> promoChecks = new ConcurrentHashMap<>();
            getAllPossibleOptions()
                    .parallel()
                    .map(proxyCardOption -> checkProxyCardOptionExists(proxyCardOption, promoChecks))
                    .forEach(existingCardIds::remove);

            // Remove entries that no longer exist
            for (Integer cardIdToRemove : existingCardIds) {
                proxyCardOptionRepository.deleteById(cardIdToRemove);
            }

            syncPromoSet(promoChecks);
            log.info("ProxyCardOption cache refresh completed successfully.");
        } catch (Exception e) {
            log.error("Error during ProxyCardOption cache refresh", e);
        }
    }

    private Integer checkProxyCardOptionExists(ProxyCardOption proxyCardOption, Map<Integer, PromoCheck> promoChecks) {
        // Always check the promo image, even when the card doesn't have the Promo set assigned
        Set<String> candidateSets = new HashSet<>(proxyCardOption.getSets());
        candidateSets.add(VtesUtils.PROMO_SET);
        Set<String> proxySets = new HashSet<>();
        for (String set : candidateSets) {
            String proxyImageUrl = proxyService.getProxyImageUrl(set, proxyCardOption.getCardId());
            if (proxyService.existImage(proxyImageUrl)) {
                proxySets.add(set);
            }
        }
        promoChecks.put(proxyCardOption.getCardId(),
                new PromoCheck(proxySets.contains(VtesUtils.PROMO_SET), proxySets.contains(VtesUtils.PFA_SET)));
        if (!proxySets.isEmpty()) {
            proxyCardOptionRepository.save(proxyCardOption.toBuilder().sets(proxySets).build());
            return proxyCardOption.getCardId();
        } else {
            return null;
        }
    }

    private void syncPromoSet(Map<Integer, PromoCheck> promoChecks) {
        boolean cryptChanged = false;
        boolean libraryChanged = false;
        for (Map.Entry<Integer, PromoCheck> entry : promoChecks.entrySet()) {
            Integer cardId = entry.getKey();
            PromoCheck promoCheck = entry.getValue();
            if (VtesUtils.isCrypt(cardId)) {
                CryptEntity entity = cryptRepository.findById(cardId).orElse(null);
                if (entity != null && entity.getSet() != null) {
                    String newSet = applyPromoImageRule(entity.getSet(), promoCheck);
                    if (!newSet.equals(entity.getSet())) {
                        log.info("Updating crypt {} promo set: '{}' -> '{}'", cardId, entity.getSet(), newSet);
                        entity.setSet(newSet);
                        cryptRepository.save(entity);
                        cryptChanged = true;
                    }
                }
            } else if (VtesUtils.isLibrary(cardId)) {
                LibraryEntity entity = libraryRepository.findById(cardId).orElse(null);
                if (entity != null && entity.getSet() != null) {
                    String newSet = applyPromoImageRule(entity.getSet(), promoCheck);
                    if (!newSet.equals(entity.getSet())) {
                        log.info("Updating library {} promo set: '{}' -> '{}'", cardId, entity.getSet(), newSet);
                        entity.setSet(newSet);
                        libraryRepository.save(entity);
                        libraryChanged = true;
                    }
                }
            }
        }
        if (cryptChanged) {
            cryptCache.refreshIndex();
        }
        if (libraryChanged) {
            libraryCache.refreshIndex();
        }
    }

    private String applyPromoImageRule(String setColumn, PromoCheck promoCheck) {
        List<String> newSets = VtesUtils.applyPromoImageRule(VtesUtils.getSets(setColumn), promoCheck.promoImageExists(), promoCheck.pfaImageExists());
        return String.join(",", newSets);
    }

    public Stream<ProxyCardOption> getAllPossibleOptions() {
        try (ResultSet<Library> libraryResultSet = libraryCache.selectAll(); ResultSet<Crypt> cryptResultSet = cryptCache.selectAll()) {
            Stream<ProxyCardOption> libraryStream = libraryResultSet.stream().map(this::libraryToProxyCardOption);
            Stream<ProxyCardOption> cryptStream = cryptResultSet.stream().map(this::cryptToProxyCardOption);
            return Stream.concat(libraryStream, cryptStream);
        }
    }

    private ProxyCardOption libraryToProxyCardOption(Library card) {
        Set<String> sets = getSetsAbbrev(card.getSets());
        return ProxyCardOption.builder()
                .cardId(card.getId())
                .cardName(card.getName())
                .sets(sets)
                .build();
    }

    private ProxyCardOption cryptToProxyCardOption(Crypt card) {
        Set<String> sets = getSetsAbbrev(card.getSets());
        return ProxyCardOption.builder()
                .cardId(card.getId())
                .cardName(card.getName())
                .sets(sets)
                .build();
    }

    private Set<String> getSetsAbbrev(List<String> sets) {
        return sets.stream()
                .map(s -> s.split(":")[0])
                .collect(Collectors.toSet());
    }
}
