package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiSearchPresetMapper;
import com.vtesdecks.enums.SearchPresetScope;
import com.vtesdecks.jpa.entity.UserSearchPresetEntity;
import com.vtesdecks.jpa.repositories.UserSearchPresetRepository;
import com.vtesdecks.model.api.ApiSearchPreset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiSearchPresetServiceTest {
    private static final int USER_ID = 42;

    @Mock
    private UserSearchPresetRepository repository;
    @Mock
    private ApiSearchPresetMapper mapper;
    @InjectMocks
    private ApiSearchPresetService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(USER_ID), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSetsUserId() throws Exception {
        ApiSearchPreset preset = preset("client", "Ventrue");
        UserSearchPresetEntity entity = entity(1, "client", "Ventrue");
        when(mapper.mapToEntity(preset)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.mapToApi(entity)).thenReturn(preset);

        service.createPreset(preset);

        assertEquals(USER_ID, entity.getUserId());
        verify(repository).save(entity);
    }

    @Test
    void rejectsBlankAndLongNames() {
        assertThrows(IllegalArgumentException.class, () -> service.createPreset(preset("client", " ")));
        assertThrows(IllegalArgumentException.class, () -> service.createPreset(preset("client", "x".repeat(65))));
    }

    @Test
    void rejectsDuplicateName() {
        ApiSearchPreset preset = preset("client", "Ventrue");
        when(repository.findByUserIdAndScopeAndName(USER_ID, SearchPresetScope.CRYPT, "Ventrue"))
                .thenReturn(Optional.of(entity(1, null, "Ventrue")));

        assertThrows(IllegalArgumentException.class, () -> service.createPreset(preset));
        verify(repository, never()).save(any());
    }

    @Test
    void updateAndDeleteRejectAnotherUsersPreset() {
        when(repository.findByUserIdAndId(USER_ID, 7)).thenReturn(Optional.empty());
        when(repository.deleteByUserIdAndId(USER_ID, 7)).thenReturn(0);

        assertThrows(IllegalArgumentException.class, () -> service.updatePreset(7, preset(null, "New")));
        assertThrows(IllegalArgumentException.class, () -> service.deletePreset(7));
    }

    @Test
    void rejectsCreateAtLimit() {
        ApiSearchPreset preset = preset("client", "Ventrue");
        when(repository.countByUserId(USER_ID)).thenReturn(ApiSearchPresetService.MAX_PRESETS_PER_USER);

        assertThrows(IllegalArgumentException.class, () -> service.createPreset(preset));
        verify(repository, never()).save(any());
    }

    @Test
    void mergeSkipsExistingClientId() throws Exception {
        ApiSearchPreset preset = preset("client", "Ventrue");
        when(repository.findByUserIdAndClientId(USER_ID, "client"))
                .thenReturn(Optional.of(entity(1, "client", "Ventrue")));
        when(repository.findByUserIdOrderByScopeAscNameAsc(USER_ID)).thenReturn(List.of());

        assertTrue(service.mergePresets(List.of(preset)).isEmpty());
        verify(repository, never()).save(any());
    }

    @Test
    void mergeSkipsExistingName() throws Exception {
        ApiSearchPreset preset = preset("new-client", "Ventrue");
        when(repository.findByUserIdAndClientId(USER_ID, "new-client")).thenReturn(Optional.empty());
        when(repository.findByUserIdAndScopeAndName(USER_ID, SearchPresetScope.CRYPT, "Ventrue"))
                .thenReturn(Optional.of(entity(1, null, "Ventrue")));
        when(repository.findByUserIdOrderByScopeAscNameAsc(USER_ID)).thenReturn(List.of());

        service.mergePresets(List.of(preset));

        verify(repository, never()).save(any());
    }

    @Test
    void mergeStopsAtLimitAndReturnsFullList() throws Exception {
        ApiSearchPreset first = preset("one", "One");
        ApiSearchPreset second = preset("two", "Two");
        UserSearchPresetEntity firstEntity = entity(null, "one", "One");
        UserSearchPresetEntity stored = entity(99, "one", "One");
        when(repository.countByUserId(USER_ID)).thenReturn(99);
        when(repository.findByUserIdAndClientId(USER_ID, "one")).thenReturn(Optional.empty());
        when(repository.findByUserIdAndScopeAndName(USER_ID, SearchPresetScope.CRYPT, "One"))
                .thenReturn(Optional.empty());
        when(mapper.mapToEntity(first)).thenReturn(firstEntity);
        when(repository.findByUserIdOrderByScopeAscNameAsc(USER_ID)).thenReturn(List.of(stored));
        when(mapper.mapToApi(stored)).thenReturn(first);

        List<ApiSearchPreset> result = service.mergePresets(List.of(first, second));

        assertEquals(List.of(first), result);
        ArgumentCaptor<UserSearchPresetEntity> captor = ArgumentCaptor.forClass(UserSearchPresetEntity.class);
        verify(repository).save(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
        verify(repository, never()).findByUserIdAndClientId(USER_ID, "two");
    }

    private static ApiSearchPreset preset(String clientId, String name) {
        return ApiSearchPreset.builder()
                .clientId(clientId)
                .scope(SearchPresetScope.CRYPT)
                .name(name)
                .params(Map.of("clans", "ventrue"))
                .build();
    }

    private static UserSearchPresetEntity entity(Integer id, String clientId, String name) {
        UserSearchPresetEntity entity = new UserSearchPresetEntity();
        entity.setId(id);
        entity.setClientId(clientId);
        entity.setScope(SearchPresetScope.CRYPT);
        entity.setName(name);
        return entity;
    }
}
