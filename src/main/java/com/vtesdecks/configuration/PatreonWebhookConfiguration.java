package com.vtesdecks.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class PatreonWebhookConfiguration {

    @Value("${patreon.webhook.secret:}")
    private String secret;
}
