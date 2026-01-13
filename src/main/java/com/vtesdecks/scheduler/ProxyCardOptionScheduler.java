package com.vtesdecks.scheduler;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.redis.entity.ProxyCardOption;
import com.vtesdecks.cache.redis.repositories.ProxyCardOptionRepository;
import com.vtesdecks.service.ProxyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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


    @Transactional
    @Scheduled(cron = "${jobs.proxyCardOptionCron:0 0 3 * * *}")
    public void proxyCardOptionScheduler() {
        log.info("Starting ProxyCardOption cache refresh...");
        try {
            // Get existing entries
            Set<Integer> existingCardIds = new HashSet<>();
            proxyCardOptionRepository.findAll().forEach(proxyCardOption -> existingCardIds.add(proxyCardOption.getCardId()));

            // Check all possible options in parallel
            getAllPossibleOptions()
                    .parallel()
                    .map(this::checkProxyCardOptionExists)
                    .forEach(existingCardIds::remove);

            // Remove entries that no longer exist
            for (Integer cardIdToRemove : existingCardIds) {
                proxyCardOptionRepository.deleteById(cardIdToRemove);
            }
            log.info("ProxyCardOption cache refresh completed successfully.");
        } catch (Exception e) {
            log.error("Error during ProxyCardOption cache refresh", e);
        }
    }

    private Integer checkProxyCardOptionExists(ProxyCardOption proxyCardOption) {
        Set<String> proxySets = new HashSet<>();
        for (String set : proxyCardOption.getSets()) {
            String proxyImageUrl = proxyService.getProxyImageUrl(set, proxyCardOption.getCardId());
            if (proxyService.existImage(proxyImageUrl)) {
                proxySets.add(set);
            }
        }
        if (!proxySets.isEmpty()) {
            proxyCardOptionRepository.save(proxyCardOption.toBuilder().sets(proxySets).build());
            return proxyCardOption.getCardId();
        } else {
            return null;
        }
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
