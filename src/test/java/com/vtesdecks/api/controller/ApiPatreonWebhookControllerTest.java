package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.PatreonSignatureVerifier;
import com.vtesdecks.api.service.PatreonWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiPatreonWebhookControllerTest {

    private static final String BODY = "{\"data\":{}}";

    @Mock
    private PatreonSignatureVerifier signatureVerifier;
    @Mock
    private PatreonWebhookService webhookService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ApiPatreonWebhookController(signatureVerifier, webhookService)).build();
    }

    @Test
    void processesSupportedSignedEvent() throws Exception {
        when(signatureVerifier.isValid(any(byte[].class), eq("signature"))).thenReturn(true);

        mockMvc.perform(post("/api/1.0/webhooks/patreon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Patreon-Event", "members:update")
                        .header("X-Patreon-Signature", "signature")
                        .content(BODY))
                .andExpect(status().isNoContent());

        verify(webhookService).process(eq("members:update"), eq(BODY.getBytes(StandardCharsets.UTF_8)));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"members:create", "members:update", "members:delete", "members:pledge:create", "members:pledge:update", "members:pledge:delete"})
    void acceptsAllMembershipEvents(String event) throws Exception {
        when(signatureVerifier.isValid(any(byte[].class), eq("signature"))).thenReturn(true);
        mockMvc.perform(post("/api/1.0/webhooks/patreon")
                .header("X-Patreon-Event", event).header("X-Patreon-Signature", "signature").content(BODY))
                .andExpect(status().isNoContent());
        verify(webhookService).process(eq(event), any(byte[].class));
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        when(signatureVerifier.isValid(any(byte[].class), eq("invalid"))).thenReturn(false);

        mockMvc.perform(post("/api/1.0/webhooks/patreon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Patreon-Event", "members:update")
                        .header("X-Patreon-Signature", "invalid")
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        verify(webhookService, never()).process(any(), any());
    }

    @Test
    void acknowledgesIgnoredAuthenticatedEvent() throws Exception {
        when(signatureVerifier.isValid(any(byte[].class), eq("signature"))).thenReturn(true);

        mockMvc.perform(post("/api/1.0/webhooks/patreon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Patreon-Event", "posts:publish")
                        .header("X-Patreon-Signature", "signature")
                        .content(BODY))
                .andExpect(status().isNoContent());

        verify(webhookService, never()).process(any(), any());
    }
}
