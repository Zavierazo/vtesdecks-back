package com.vtesdecks.api.service;

import com.vtesdecks.configuration.PatreonWebhookConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatreonSignatureVerifierTest {

    private static final String SECRET = "webhook-secret";

    @Mock
    private PatreonWebhookConfiguration configuration;
    private PatreonSignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new PatreonSignatureVerifier(configuration);
    }

    @Test
    void acceptsValidSignature() throws Exception {
        byte[] body = "{\"data\":{}}".getBytes(StandardCharsets.UTF_8);
        when(configuration.getSecret()).thenReturn(SECRET);

        assertTrue(verifier.isValid(body, signature(body)));
    }

    @Test
    void rejectsInvalidOrMalformedSignature() {
        byte[] body = "{\"data\":{}}".getBytes(StandardCharsets.UTF_8);
        when(configuration.getSecret()).thenReturn(SECRET);

        assertFalse(verifier.isValid(body, "00000000000000000000000000000000"));
        assertFalse(verifier.isValid(body, "not-hex"));
        assertFalse(verifier.isValid(body, null));
    }

    private String signature(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacMD5");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacMD5"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
