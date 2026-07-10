package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.ApiCommentService;
import com.vtesdecks.api.service.ApiDeckService;
import com.vtesdecks.api.service.ApiUserService;
import com.vtesdecks.enums.CardPrintingPreference;
import com.vtesdecks.jpa.entity.UserEntity;
import com.vtesdecks.jpa.repositories.UserRepository;
import com.vtesdecks.model.api.ApiResponse;
import com.vtesdecks.model.api.ApiUserSettings;
import com.vtesdecks.service.DeckUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ApiUserControllerTest {
    private static final int USER_ID = 42;

    @Mock
    private DeckUserService deckUserService;
    @Mock
    private ApiDeckService deckService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApiCommentService apiCommentService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApiUserService userService;
    @InjectMocks
    private ApiUserController controller;

    private UserEntity user;

    @BeforeEach
    public void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(String.valueOf(USER_ID), null, List.of()));
        user = new UserEntity();
        user.setId(USER_ID);
        user.setUsername("testuser");
        user.setEmail("testuser@example.com");
        user.setDisplayName("Test User");
        user.setCardPrintingPreference(CardPrintingPreference.NEWEST);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @AfterEach
    public void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    public void shouldPersistCardPrintingPreference() {
        ApiUserSettings settings = new ApiUserSettings();
        settings.setCardPrintingPreference(CardPrintingPreference.FIRST);

        ApiResponse response = controller.changeSettings(settings);

        assertTrue(response.getSuccessful());
        verify(userRepository).save(argThat(saved -> saved.getCardPrintingPreference() == CardPrintingPreference.FIRST));
    }

    @Test
    public void shouldKeepCardPrintingPreferenceWhenNotSent() {
        ApiUserSettings settings = new ApiUserSettings();
        settings.setDisplayName("New Name");

        ApiResponse response = controller.changeSettings(settings);

        assertTrue(response.getSuccessful());
        verify(userRepository).save(argThat(saved -> saved.getCardPrintingPreference() == CardPrintingPreference.NEWEST));
    }

    @Test
    public void shouldNotSaveWhenNothingChanged() {
        ApiUserSettings settings = new ApiUserSettings();

        ApiResponse response = controller.changeSettings(settings);

        assertNull(response.getSuccessful());
        verify(userRepository, never()).save(user);
        assertEquals(CardPrintingPreference.NEWEST, user.getCardPrintingPreference());
    }
}
