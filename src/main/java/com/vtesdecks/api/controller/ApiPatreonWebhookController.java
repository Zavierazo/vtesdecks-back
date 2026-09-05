package com.vtesdecks.api.controller;

import com.vtesdecks.api.service.PatreonSignatureVerifier;
import com.vtesdecks.api.service.PatreonWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@RestController
@RequestMapping("/api/1.0/webhooks")
@RequiredArgsConstructor
@Slf4j
public class ApiPatreonWebhookController {

    private static final Set<String> SUPPORTED_EVENTS = Set.of("members:create", "members:update");

    private final PatreonSignatureVerifier signatureVerifier;
    private final PatreonWebhookService webhookService;

    @PostMapping("/patreon")
    public ResponseEntity<Void> patreon(
            @RequestHeader(value = "X-Patreon-Event", required = false) String event,
            @RequestHeader(value = "X-Patreon-Signature", required = false) String signature,
            @RequestBody byte[] body) {
        log.info("Received Patreon webhook event {} with body {}", event,
                new String(body, StandardCharsets.UTF_8));
        if (!signatureVerifier.isValid(body, signature)) {
            log.warn("Rejected Patreon webhook with an invalid signature");
            return ResponseEntity.status(401).build();
        }
        if (!SUPPORTED_EVENTS.contains(event)) {
            log.warn("Rejected unsupported Patreon webhook event {}", event);
            return ResponseEntity.badRequest().build();
        }
        try {
            webhookService.process(body);
            return ResponseEntity.noContent().build();
        } catch (IOException | IllegalArgumentException exception) {
            log.warn("Rejected malformed Patreon webhook: {}", exception.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
