package com.vtesdecks.scheduler;

import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.api.controller.ApiAdminController;
import com.vtesdecks.api.service.ApiAdminSchedulerService;
import com.vtesdecks.jpa.entity.CardI18nImageEntity.CardI18nImageId;
import com.vtesdecks.jpa.repositories.CryptRepository;
import com.vtesdecks.jpa.repositories.LibraryRepository;
import com.vtesdecks.service.CardI18nImageService;
import com.vtesdecks.service.I18nImageClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class I18nImageSchedulerTest {
    @Mock private CryptRepository cryptRepository;
    @Mock private LibraryRepository libraryRepository;
    @Mock private I18nImageClient imageClient;
    @Mock private CardI18nImageService imageService;
    @Mock private CryptCache cryptCache;
    @Mock private LibraryCache libraryCache;

    @Test
    void scansTheCatalogAndConfiguredLocalesBeforePublishingChanges() {
        when(cryptRepository.selectIds()).thenReturn(List.of(200001));
        when(libraryRepository.selectIds()).thenReturn(List.of(100001));
        when(imageClient.check(any())).thenAnswer(invocation ->
                new I18nImageClient.Check(invocation.getArgument(0), I18nImageClient.Availability.PRESENT));
        when(imageService.synchronize(anyList())).thenReturn(new CardI18nImageService.Changes(4, 0, 0, true, true));

        scheduler("es, FR,es").scanImages();

        for (int cardId : Set.of(200001, 100001)) {
            for (String locale : Set.of("es", "fr")) {
                verify(imageClient).check(new CardI18nImageId(cardId, locale));
            }
        }
        var order = inOrder(imageService, cryptCache, libraryCache);
        order.verify(imageService).synchronize(argThat(checks -> checks.size() == 4));
        order.verify(cryptCache).refreshIndex();
        order.verify(libraryCache).refreshIndex();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void manualAdminActionRunsDiscoveryAndReportsCounters(CapturedOutput output) throws Exception {
        when(cryptRepository.selectIds()).thenReturn(List.of(200001));
        when(libraryRepository.selectIds()).thenReturn(List.of(100001));
        when(imageClient.check(new CardI18nImageId(200001, "es"))).thenReturn(
                new I18nImageClient.Check(new CardI18nImageId(200001, "es"), I18nImageClient.Availability.PRESENT));
        when(imageClient.check(new CardI18nImageId(100001, "es"))).thenReturn(
                new I18nImageClient.Check(new CardI18nImageId(100001, "es"), I18nImageClient.Availability.FAILED));
        when(imageService.synchronize(anyList())).thenReturn(new CardI18nImageService.Changes(1, 0, 0, true, false));
        ApiAdminSchedulerService admin = mock(ApiAdminSchedulerService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(admin, "i18nImageScheduler", scheduler("es"));
        var mvc = MockMvcBuilders.standaloneSetup(new ApiAdminController(null, admin, null)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", null, List.of()));
        try {
            mvc.perform(post("/api/1.0/admin/schedulers/i18n-images")).andExpect(status().isNoContent());
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertTrue(output.getOut().contains("checked=2, found=1, added=1, updated=0, removed=0, failed=1"));
        verify(cryptCache).refreshIndex();
        verifyNoInteractions(libraryCache);
    }

    @Test
    void boundsConcurrencyAndSkipsOverlappingManualAndScheduledRuns() throws Exception {
        when(cryptRepository.selectIds()).thenReturn(IntStream.range(200001, 200021).boxed().toList());
        when(libraryRepository.selectIds()).thenReturn(List.of());
        CountDownLatch started = new CountDownLatch(8);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        when(imageClient.check(any())).thenAnswer(invocation -> {
            int count = active.incrementAndGet();
            maximum.accumulateAndGet(count, Math::max);
            started.countDown();
            try {
                assertTrue(release.await(10, TimeUnit.SECONDS));
                return new I18nImageClient.Check(invocation.getArgument(0), I18nImageClient.Availability.MISSING);
            } finally {
                active.decrementAndGet();
            }
        });
        when(imageService.synchronize(anyList())).thenReturn(new CardI18nImageService.Changes(0, 0, 0, false, false));
        I18nImageScheduler scheduler = scheduler("es");
        try (var caller = Executors.newSingleThreadExecutor()) {
            var first = caller.submit(scheduler::scanImages);
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                scheduler.scanImages();
                verify(cryptRepository, times(1)).selectIds();
            } finally {
                release.countDown();
            }
            first.get(10, TimeUnit.SECONDS);
        }
        assertEquals(8, maximum.get());
        verify(imageClient, times(20)).check(any());
        verifyNoInteractions(cryptCache, libraryCache);
    }

    @Test
    void doesNotRefreshCachesWhenTheWriteTransactionFailsAndAllowsRetry() {
        when(cryptRepository.selectIds()).thenReturn(List.of());
        when(libraryRepository.selectIds()).thenReturn(List.of());
        when(imageService.synchronize(anyList())).thenThrow(new IllegalStateException("database unavailable"))
                .thenReturn(new CardI18nImageService.Changes(0, 0, 0, false, false));
        I18nImageScheduler scheduler = scheduler("es");

        assertThrows(IllegalStateException.class, scheduler::scanImages);
        assertDoesNotThrow(scheduler::scanImages);

        verify(imageService, times(2)).synchronize(List.of());
        verifyNoInteractions(cryptCache, libraryCache);
    }

    private I18nImageScheduler scheduler(String locales) {
        return new I18nImageScheduler(cryptRepository, libraryRepository, imageClient, imageService,
                cryptCache, libraryCache, locales);
    }
}
