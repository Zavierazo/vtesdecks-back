package com.vtesdecks.integration;

import feign.Request;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ArchonClientTest {

    @Test
    void retriesRateLimitedResponsesWithRetryAfter() {
        Request request = Request.create(Request.HttpMethod.GET, "https://api.archon.vekn.net/v1/tournaments", Map.of(), new byte[0], StandardCharsets.UTF_8);
        Response response = Response.builder()
                .status(429)
                .reason("Too Many Requests")
                .headers(Map.of("Retry-After", List.of("7")))
                .request(request)
                .build();
        ErrorDecoder decoder = new ArchonClient.Configuration().archonErrorDecoder();

        RetryableException exception = assertInstanceOf(RetryableException.class, decoder.decode("ArchonClient#tournaments", response));

        assertNotNull(exception.retryAfter());
    }

    @Test
    void retriesRateLimitedResponsesWithoutRetryAfter() {
        Request request = Request.create(Request.HttpMethod.GET, "https://api.archon.vekn.net/v1/tournaments", Map.of(), new byte[0], StandardCharsets.UTF_8);
        Response response = Response.builder()
                .status(429)
                .reason("Too Many Requests")
                .headers(Map.of())
                .request(request)
                .build();
        ErrorDecoder decoder = new ArchonClient.Configuration().archonErrorDecoder();

        assertInstanceOf(RetryableException.class, decoder.decode("ArchonClient#tournaments", response));
    }
}
