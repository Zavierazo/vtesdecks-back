package com.vtesdecks.api.controller;

import com.vtesdecks.api.GlobalExceptionHandler;
import com.vtesdecks.model.ArchetypeCardRequirement;
import com.vtesdecks.model.api.ApiDeckArchetype;
import com.vtesdecks.service.DeckArchetypeService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiDeckArchetypeRequirementsTest {
    private DeckArchetypeService service;
    private MockMvc mvc;

    @BeforeEach void setup() {
        service = mock(DeckArchetypeService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ApiDeckArchetypeController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void mutationsRetainAdminProtection() throws Exception {
        assertArrayEquals(new String[]{"ADMIN"}, ApiDeckArchetypeController.class.getMethod("create", HttpServletRequest.class, ApiDeckArchetype.class).getAnnotation(Secured.class).value());
        assertArrayEquals(new String[]{"ADMIN"}, ApiDeckArchetypeController.class.getMethod("update", HttpServletRequest.class, Integer.class, ApiDeckArchetype.class).getAnnotation(Secured.class).value());
    }

    @Test void putAcceptsAndReturnsRequirements() throws Exception {
        var rules = List.of(new ArchetypeCardRequirement(200001, 4));
        when(service.update(eq(1), any(), any())).thenReturn(Optional.of(ApiDeckArchetype.builder().id(1).cardRequirements(rules).build()));
        mvc.perform(put("/api/1.0/deck-archetype/1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardRequirements\":[{\"cardId\":200001,\"minimumQuantity\":4}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cardRequirements[0].minimumQuantity").value(4));
        verify(service).update(eq(1), argThat(api -> rules.equals(api.getCardRequirements())), any());
    }

    @Test void invalidRulesReturnBadRequest() throws Exception {
        when(service.create(any(), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid requirements"));
        mvc.perform(post("/api/1.0/deck-archetype").contentType(MediaType.APPLICATION_JSON).content("{\"cardRequirements\":[{\"cardId\":99,\"minimumQuantity\":0}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test void fractionalQuantitiesAreNotSilentlyTruncated() throws Exception {
        mvc.perform(post("/api/1.0/deck-archetype").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardRequirements\":[{\"cardId\":200001,\"minimumQuantity\":1.5}]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
