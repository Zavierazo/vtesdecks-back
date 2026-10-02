package com.vtesdecks.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.api.mapper.DeckArchetypeMapper;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.cache.DeckArchetypeIndex;
import com.vtesdecks.cache.DeckCardIndex;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.redis.repositories.DeckArchetypeRedisRepository;
import com.vtesdecks.jpa.entity.DeckArchetypeEntity;
import com.vtesdecks.jpa.entity.converter.ArchetypeCardRequirementsConverter;
import com.vtesdecks.jpa.repositories.DeckArchetypeRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.ArchetypeCardRequirement;
import com.vtesdecks.model.api.ApiDeckArchetype;
import com.vtesdecks.scheduler.DeckArchetypeScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeckArchetypeRequirementsTest {
    @Mock DeckCardIndex deckCardIndex;
    @Mock DeckArchetypeRepository repository;
    @Mock DeckArchetypeMapper mapper;
    @Mock DeckService deckService;
    @Mock DeckArchetypeIndex deckArchetypeIndex;
    @Mock DeckArchetypeRedisRepository redisRepository;
    @Mock DeckArchetypeScheduler scheduler;
    @Mock MessageProducer producer;
    @Mock CryptCache cryptCache;
    @Mock LibraryCache libraryCache;
    @InjectMocks DeckArchetypeService service;

    private DeckArchetypeEntity existing() {
        DeckArchetypeEntity entity = DeckArchetypeEntity.builder().id(1)
                .cardRequirements(List.of(new ArchetypeCardRequirement(200001, 4))).build();
        when(repository.findById(1)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        return entity;
    }

    @Test void omittedRequirementsPreserveRulesAndDoNotReclassify() {
        DeckArchetypeEntity entity = existing();
        service.update(1, new ApiDeckArchetype(), "EUR");
        assertEquals(List.of(new ArchetypeCardRequirement(200001, 4)), entity.getCardRequirements());
        verifyNoInteractions(scheduler);
    }

    @Test void explicitEmptyRequirementsClearAndReclassify() {
        DeckArchetypeEntity entity = existing();
        service.update(1, ApiDeckArchetype.builder().cardRequirements(List.of()).build(), "EUR");
        assertTrue(entity.getCardRequirements().isEmpty());
        verify(scheduler).updateDeckArchetype(1);
    }

    @Test void changedMinimumReclassifies() {
        DeckArchetypeEntity entity = existing();
        when(cryptCache.get(200001)).thenReturn(mock(Crypt.class));
        service.update(1, ApiDeckArchetype.builder().cardRequirements(List.of(new ArchetypeCardRequirement(200001, 5))).build(), "EUR");
        assertEquals(5, entity.getCardRequirements().getFirst().getMinimumQuantity());
        verify(scheduler).updateDeckArchetype(1);
    }

    @Test void invalidRequirementsAreRejectedBeforeSaving() {
        List<List<ArchetypeCardRequirement>> invalid = List.of(
                List.of(new ArchetypeCardRequirement(999, 1)),
                List.of(new ArchetypeCardRequirement(200001, 0)),
                List.of(new ArchetypeCardRequirement(200001, -1)),
                List.of(new ArchetypeCardRequirement(null, 1)),
                List.of(new ArchetypeCardRequirement(200001, null)),
                Arrays.asList((ArchetypeCardRequirement) null));
        for (var rules : invalid) {
            assertThrows(ResponseStatusException.class, () -> service.create(ApiDeckArchetype.builder().cardRequirements(rules).build(), "EUR"));
        }
        verifyNoInteractions(repository);
    }

    @Test void duplicateCardIdsAreRejected() {
        when(cryptCache.get(200001)).thenReturn(mock(Crypt.class));
        assertThrows(ResponseStatusException.class, () -> service.create(ApiDeckArchetype.builder().cardRequirements(List.of(
                new ArchetypeCardRequirement(200001, 1), new ArchetypeCardRequirement(200001, 4))).build(), "EUR"));
        verifyNoInteractions(repository);
    }

    @Test void failureIsNotReportedAsSuccessfulSave() {
        existing();
        doThrow(new RuntimeException("reclassification failed")).when(scheduler).updateDeckArchetype(1);
        assertThrows(RuntimeException.class, () -> service.update(1, ApiDeckArchetype.builder().cardRequirements(List.of()).build(), "EUR"));
        verifyNoInteractions(deckArchetypeIndex);
    }

    @Test void requirementsRoundTripThroughJsonAndPersistenceConverter() throws Exception {
        var rules = List.of(new ArchetypeCardRequirement(200001, 4), new ArchetypeCardRequirement(100001, 10));
        var converter = new ArchetypeCardRequirementsConverter();
        assertEquals(rules, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(rules)));
        assertEquals(List.of(), converter.convertToEntityAttribute(null));
        ObjectMapper json = new ObjectMapper();
        var api = ApiDeckArchetype.builder().cardRequirements(rules).build();
        assertEquals(rules, json.readValue(json.writeValueAsString(api), ApiDeckArchetype.class).getCardRequirements());
    }
}
