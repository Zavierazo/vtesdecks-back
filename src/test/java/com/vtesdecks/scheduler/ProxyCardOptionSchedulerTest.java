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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProxyCardOptionSchedulerTest {
    @Mock
    private ProxyCardOptionRepository proxyCardOptionRepository;
    @Mock
    private LibraryCache libraryCache;
    @Mock
    private CryptCache cryptCache;
    @Mock
    private ProxyService proxyService;
    @Mock
    private CryptRepository cryptRepository;
    @Mock
    private LibraryRepository libraryRepository;
    @Mock
    private ResultSet<Library> libraryResultSet;
    @Mock
    private ResultSet<Crypt> cryptResultSet;
    @InjectMocks
    private ProxyCardOptionScheduler scheduler;

    @BeforeEach
    public void setUp() {
        when(proxyCardOptionRepository.findAll()).thenReturn(Collections.emptyList());
        when(libraryCache.selectAll()).thenReturn(libraryResultSet);
        when(cryptCache.selectAll()).thenReturn(cryptResultSet);
        when(libraryResultSet.stream()).thenReturn(Stream.empty());
        when(cryptResultSet.stream()).thenReturn(Stream.empty());
        when(proxyService.getProxyImageUrl(anyString(), anyInt()))
                .thenAnswer(invocation -> invocation.getArgument(0) + "/" + invocation.getArgument(1));
    }

    private Crypt crypt(Integer id, List<String> sets) {
        Crypt card = new Crypt();
        card.setId(id);
        card.setName("Crypt " + id);
        card.setSets(sets);
        return card;
    }

    private Library library(Integer id, List<String> sets) {
        Library card = new Library();
        card.setId(id);
        card.setName("Library " + id);
        card.setSets(sets);
        return card;
    }

    @Test
    public void addsPromoSetWhenPromoImageExistsAndCardDoesNotHaveIt() {
        when(cryptResultSet.stream()).thenReturn(Stream.of(crypt(200001, List.of("V5:PB"))));
        when(proxyService.existImage("V5/200001")).thenReturn(true);
        when(proxyService.existImage("Promo/200001")).thenReturn(true);
        CryptEntity entity = new CryptEntity();
        entity.setId(200001);
        entity.setSet("V5:PB");
        when(cryptRepository.findById(200001)).thenReturn(Optional.of(entity));

        scheduler.proxyCardOptionScheduler();

        ArgumentCaptor<CryptEntity> entityCaptor = ArgumentCaptor.forClass(CryptEntity.class);
        verify(cryptRepository).save(entityCaptor.capture());
        assertEquals("V5:PB,Promo:1", entityCaptor.getValue().getSet());
        verify(cryptCache).refreshIndex();

        ArgumentCaptor<ProxyCardOption> optionCaptor = ArgumentCaptor.forClass(ProxyCardOption.class);
        verify(proxyCardOptionRepository).save(optionCaptor.capture());
        assertEquals(Set.of("V5", "Promo"), optionCaptor.getValue().getSets());
    }

    @Test
    public void removesPromoSetWhenOnlyPfaImageExists() {
        when(libraryResultSet.stream()).thenReturn(Stream.of(library(100001, List.of("Promo:20190601", "PFA:1"))));
        when(proxyService.existImage("Promo/100001")).thenReturn(false);
        when(proxyService.existImage("PFA/100001")).thenReturn(true);
        LibraryEntity entity = new LibraryEntity();
        entity.setId(100001);
        entity.setSet("Promo:20190601,PFA:1");
        when(libraryRepository.findById(100001)).thenReturn(Optional.of(entity));

        scheduler.proxyCardOptionScheduler();

        ArgumentCaptor<LibraryEntity> entityCaptor = ArgumentCaptor.forClass(LibraryEntity.class);
        verify(libraryRepository).save(entityCaptor.capture());
        assertEquals("PFA:1", entityCaptor.getValue().getSet());
        verify(libraryCache).refreshIndex();

        ArgumentCaptor<ProxyCardOption> optionCaptor = ArgumentCaptor.forClass(ProxyCardOption.class);
        verify(proxyCardOptionRepository).save(optionCaptor.capture());
        assertEquals(Set.of("PFA"), optionCaptor.getValue().getSets());
    }

    @Test
    public void keepsPromoSetWhenNoImageExists() {
        when(cryptResultSet.stream()).thenReturn(Stream.of(crypt(200002, List.of("Promo:20190601"))));
        when(proxyService.existImage(anyString())).thenReturn(false);
        CryptEntity entity = new CryptEntity();
        entity.setId(200002);
        entity.setSet("Promo:20190601");
        when(cryptRepository.findById(200002)).thenReturn(Optional.of(entity));

        scheduler.proxyCardOptionScheduler();

        verify(cryptRepository, never()).save(any());
        verify(cryptCache, never()).refreshIndex();
        verify(proxyCardOptionRepository, never()).save(any());
    }

    @Test
    public void doesNothingWhenCardHasNoPromoAndNoPromoImage() {
        when(cryptResultSet.stream()).thenReturn(Stream.of(crypt(200003, List.of("V5:PB"))));
        when(proxyService.existImage("V5/200003")).thenReturn(true);
        when(proxyService.existImage("Promo/200003")).thenReturn(false);
        CryptEntity entity = new CryptEntity();
        entity.setId(200003);
        entity.setSet("V5:PB");
        when(cryptRepository.findById(200003)).thenReturn(Optional.of(entity));

        scheduler.proxyCardOptionScheduler();

        verify(cryptRepository, never()).save(any());
        verify(cryptCache, never()).refreshIndex();

        ArgumentCaptor<ProxyCardOption> optionCaptor = ArgumentCaptor.forClass(ProxyCardOption.class);
        verify(proxyCardOptionRepository).save(optionCaptor.capture());
        assertEquals(Set.of("V5"), optionCaptor.getValue().getSets());
        assertFalse(optionCaptor.getValue().getSets().contains("Promo"));
    }

    @Test
    public void removesOrphanEntriesFromRedis() {
        ProxyCardOption orphan = ProxyCardOption.builder().cardId(100999).cardName("Orphan").sets(Set.of("Promo")).build();
        when(proxyCardOptionRepository.findAll()).thenReturn(List.of(orphan));

        scheduler.proxyCardOptionScheduler();

        verify(proxyCardOptionRepository).deleteById(100999);
    }
}
