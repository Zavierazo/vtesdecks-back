package com.vtesdecks.api.service;

import com.vtesdecks.configuration.PatreonWebhookConfiguration;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class PatreonSignatureVerifier {

    private static final String HMAC_MD5 = "HmacMD5";

    private final PatreonWebhookConfiguration configuration;

    public boolean isValid(byte[] body, String signature) {
        if (body == null || StringUtils.isBlank(signature) || StringUtils.isBlank(configuration.getSecret())) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_MD5);
            mac.init(new SecretKeySpec(configuration.getSecret().getBytes(StandardCharsets.UTF_8), HMAC_MD5));
            byte[] expected = mac.doFinal(body);
            byte[] provided = HexFormat.of().parseHex(signature.trim());
            return MessageDigest.isEqual(expected, provided);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            return false;
        }
    }
}
