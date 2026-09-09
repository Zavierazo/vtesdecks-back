package com.vtesdecks.api.controller;

import com.vtesdecks.api.GlobalExceptionHandler;
import com.vtesdecks.api.service.ApiCollectionService;
import com.vtesdecks.api.service.ApiWishlistService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiCollectionPublicControllerTest {
    @Test
    void distinguishesUnavailableBindersFromTransientApiFailures() throws Exception {
        ApiCollectionService service = mock(ApiCollectionService.class);
        when(service.getPublicBinder("missing")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        when(service.getPublicBinder("failure")).thenThrow(new Exception("Database unavailable"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ApiCollectionController(service, mock(ApiWishlistService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/1.0/collections/binders/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/1.0/collections/binders/failure")).andExpect(status().isInternalServerError());
    }
}
