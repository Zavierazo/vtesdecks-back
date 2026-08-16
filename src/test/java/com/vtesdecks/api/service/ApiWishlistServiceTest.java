package com.vtesdecks.api.service;

import com.vtesdecks.api.mapper.ApiWishlistMapper;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.LibraryCache;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.jpa.repositories.WishlistCardRepository;
import com.vtesdecks.jpa.repositories.WishlistCardRepositoryCustom;
import com.vtesdecks.model.api.ApiWishlistCard;
import com.vtesdecks.model.api.ApiWishlistPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApiWishlistServiceTest {
    private static final int USER_ID = 42;
    private static final String USERNAME = "testuser";
    private static final String CURRENCY = "EUR";

    @Mock
    private UserRepository userRepository;
    @Mock
    private WishlistCardRepository wishlistCardRepository;
    @Mock
    private WishlistCardRepositoryCustom wishlistCardRepositoryCustom;
    @Mock
    private ApiWishlistMapper apiWishlistMapper;
    @Mock
    private CryptCache cryptCache;
    @Mock
    private LibraryCache libraryCache;
    @InjectMocks
    private ApiWishlistService service;

    @BeforeEach
    public void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(USER_ID), null, List.of()));
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setWishlistPublicVisibility(true);
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(apiWishlistMapper.mapWishlistPage(any(), eq(CURRENCY))).thenReturn(new ApiWishlistPage<>());
        lenient().when(wishlistCardRepositoryCustom.findByDynamicFilters(eq(USER_ID), any(), any()))
                .thenReturn(emptyEntityPage());
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Page<com.vtesdecks.jpa.entity.WishlistCardEntity> emptyEntityPage() {
        return new PageImpl<>(List.of());
    }

    @Test
    public void shouldRestrictWishlistByCardIds() throws Exception {
        service.searchWishlist(0, 20, null, null, new HashMap<>(), List.of(200011, 200013), CURRENCY);

        ArgumentCaptor<Map<String, String>> filtersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(wishlistCardRepositoryCustom).findByDynamicFilters(eq(USER_ID), filtersCaptor.capture(), any(Pageable.class));
        assertEquals("200011,200013", filtersCaptor.getValue().get("cardId"));
    }

    @Test
    public void shouldBehaveLikeGetWhenCardIdsAreNull() throws Exception {
        Map<String, String> params = new HashMap<>(Map.of("set", "V5"));

        service.searchWishlist(0, 20, null, null, params, null, CURRENCY);

        ArgumentCaptor<Map<String, String>> filtersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(wishlistCardRepositoryCustom).findByDynamicFilters(eq(USER_ID), filtersCaptor.capture(), any(Pageable.class));
        assertEquals("V5", filtersCaptor.getValue().get("set"));
        assertFalse(filtersCaptor.getValue().containsKey("cardId"));
    }

    @Test
    public void shouldReturnEmptyPageWhenCardIdsAreEmpty() throws Exception {
        ApiWishlistPage<ApiWishlistCard> result = service.searchWishlist(0, 20, null, null, new HashMap<>(), List.of(), CURRENCY);

        assertEquals(0, result.getTotalPages());
        assertEquals(0L, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
        assertTrue(result.getPublicVisibility());
        verify(wishlistCardRepositoryCustom, never()).findByDynamicFilters(any(), any(), any());
    }

    @Test
    public void shouldCombineCardIdsWithOtherFilters() throws Exception {
        Map<String, String> params = new HashMap<>(Map.of("priority", "HIGH", "cardType", "crypt"));

        service.searchWishlist(0, 20, null, null, params, List.of(200011), CURRENCY);

        ArgumentCaptor<Map<String, String>> filtersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(wishlistCardRepositoryCustom).findByDynamicFilters(eq(USER_ID), filtersCaptor.capture(), any(Pageable.class));
        assertEquals("HIGH", filtersCaptor.getValue().get("priority"));
        assertEquals("crypt", filtersCaptor.getValue().get("cardType"));
        assertEquals("200011", filtersCaptor.getValue().get("cardId"));
    }

    @Test
    public void shouldRejectOversizedCardIdList() {
        List<Integer> cardIds = IntStream.rangeClosed(1, 6001).boxed().toList();

        assertThrows(IllegalArgumentException.class,
                () -> service.searchWishlist(0, 20, null, null, new HashMap<>(), cardIds, CURRENCY));
    }

    @Test
    public void shouldReturnNullWhenPublicWishlistUserDoesNotExist() throws Exception {
        when(userRepository.findByUsername(USERNAME)).thenReturn(null);

        assertNull(service.searchUserPublicWishlist(USERNAME, 0, 20, null, null, new HashMap<>(), List.of(200011), CURRENCY));
        verify(wishlistCardRepositoryCustom, never()).findByDynamicFilters(any(), any(), any());
    }

    @Test
    public void shouldReturnNullWhenPublicWishlistIsPrivate() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setWishlistPublicVisibility(false);
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);

        assertNull(service.searchUserPublicWishlist(USERNAME, 0, 20, null, null, new HashMap<>(), List.of(200011), CURRENCY));
        verify(wishlistCardRepositoryCustom, never()).findByDynamicFilters(any(), any(), any());
    }

    @Test
    public void shouldSearchPublicWishlistWhenVisible() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setWishlistPublicVisibility(true);
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);

        ApiWishlistPage<ApiWishlistCard> result = service.searchUserPublicWishlist(USERNAME, 0, 20, null, null, new HashMap<>(), List.of(200011), CURRENCY);

        assertTrue(result.getPublicVisibility());
        ArgumentCaptor<Map<String, String>> filtersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(wishlistCardRepositoryCustom).findByDynamicFilters(eq(USER_ID), filtersCaptor.capture(), any(Pageable.class));
        assertEquals("200011", filtersCaptor.getValue().get("cardId"));
    }

    @Test
    public void shouldReturnEmptyPageForPublicWishlistWhenCardIdsAreEmpty() throws Exception {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setWishlistPublicVisibility(true);
        when(userRepository.findByUsername(USERNAME)).thenReturn(user);

        ApiWishlistPage<ApiWishlistCard> result = service.searchUserPublicWishlist(USERNAME, 0, 20, null, null, new HashMap<>(), List.of(), CURRENCY);

        assertEquals(0L, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
        assertTrue(result.getPublicVisibility());
        verify(wishlistCardRepositoryCustom, never()).findByDynamicFilters(any(), any(), any());
    }
}
