package com.vtesdecks.scheduler;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.jpa.entity.CardI18nImageEntity.CardI18nImageId;
import com.vtesdecks.jpa.repositories.CryptRepository;
import com.vtesdecks.jpa.repositories.LibraryRepository;
import com.vtesdecks.service.CardI18nImageService;
import com.vtesdecks.service.I18nImageClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class I18nImageScheduler {
    private final CryptRepository cryptRepository;
    private final LibraryRepository libraryRepository;
    private final I18nImageClient imageClient;
    private final CardI18nImageService imageService;
    private final CryptCache cryptCache;
    private final LibraryCache libraryCache;
    private final List<String> locales;
    private final AtomicBoolean running = new AtomicBoolean();

    public I18nImageScheduler(CryptRepository cryptRepository, LibraryRepository libraryRepository,
                              I18nImageClient imageClient, CardI18nImageService imageService,
                              CryptCache cryptCache, LibraryCache libraryCache,
                              @Value("${jobs.i18nImagesLocales:es,fr,pt}") String locales) {
        this.cryptRepository = cryptRepository;
        this.libraryRepository = libraryRepository;
        this.imageClient = imageClient;
        this.imageService = imageService;
        this.cryptCache = cryptCache;
        this.libraryCache = libraryCache;
        this.locales = Arrays.stream(locales.split(","))
                .map(String::strip).map(locale -> locale.toLowerCase(Locale.ROOT)).distinct().toList();
        if (this.locales.isEmpty() || this.locales.stream().anyMatch(locale -> !locale.matches("[a-z]{2}"))) {
            throw new IllegalArgumentException("jobs.i18nImagesLocales must contain comma-separated two-letter locales");
        }
    }

    @Scheduled(cron = "${jobs.i18nImagesCron:0 30 3 * * *}")
    public void scanImages() {
        if (!running.compareAndSet(false, true)) {
            log.info("Skipping i18n image scan: another scan is running");
            return;
        }
        long start = System.nanoTime();
        log.info("Starting i18n image scan for locales {}", locales);
        try {
            Set<Integer> cardIds = new HashSet<>(cryptRepository.selectIds());
            cardIds.addAll(libraryRepository.selectIds());
            List<Callable<I18nImageClient.Check>> tasks = new ArrayList<>();
            for (Integer cardId : cardIds) {
                for (String locale : locales) {
                    tasks.add(() -> imageClient.check(new CardI18nImageId(cardId, locale)));
                }
            }
            List<I18nImageClient.Check> checks = new ArrayList<>();
            try (var executor = Executors.newFixedThreadPool(8)) {
                for (var future : executor.invokeAll(tasks)) {
                    checks.add(future.get());
                }
            }
            // The transactional service commits before the cache readers can see these changes.
            CardI18nImageService.Changes changes = imageService.synchronize(checks);
            if (changes.cryptChanged()) {
                cryptCache.refreshIndex();
            }
            if (changes.libraryChanged()) {
                libraryCache.refreshIndex();
            }
            long found = checks.stream().filter(check -> check.availability() == I18nImageClient.Availability.PRESENT).count();
            long failed = checks.stream().filter(check -> check.availability() == I18nImageClient.Availability.FAILED).count();
            log.info("I18n image scan completed: checked={}, found={}, added={}, updated={}, removed={}, failed={}, durationMs={}",
                    checks.size(), found, changes.added(), changes.updated(), changes.removed(), failed,
                    (System.nanoTime() - start) / 1_000_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("I18n image scan interrupted", e);
        } catch (ExecutionException | RuntimeException e) {
            log.error("I18n image scan failed", e);
            throw new IllegalStateException("I18n image scan failed", e);
        } finally {
            running.set(false);
        }
    }
}
