package com.vtesdecks.service;

import com.vtesdecks.jpa.entity.CardI18nImageEntity.CardI18nImageId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;

import static com.vtesdecks.service.I18nImageClient.Availability.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class I18nImageClientTest {
    private final RestTemplate restTemplate = new RestTemplate();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    private final I18nImageClient client = new I18nImageClient(restTemplate);
    private final CardI18nImageId id = new CardI18nImageId(201529, "es");

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"OK", "NO_CONTENT", "NOT_FOUND", "FORBIDDEN", "TOO_MANY_REQUESTS", "INTERNAL_SERVER_ERROR", "FOUND"})
    void distinguishesMissingImagesFromTemporaryFailures(HttpStatus status) {
        server.expect(requestTo("https://cdn.vtesdecks.com/img/cards/es/201529.jpg"))
                .andExpect(method(HttpMethod.HEAD)).andRespond(withStatus(status));

        I18nImageClient.Check result = client.check(id);

        assertEquals(id, result.id());
        assertEquals(status.is2xxSuccessful() ? PRESENT : status == HttpStatus.NOT_FOUND ? MISSING : FAILED,
                result.availability());
        server.verify();
    }

    @Test
    void preservesAvailabilityWhenTheConnectionTimesOut() {
        server.expect(requestTo("https://cdn.vtesdecks.com/img/cards/es/201529.jpg"))
                .andExpect(method(HttpMethod.HEAD)).andRespond(withException(new SocketTimeoutException("timeout")));

        assertEquals(FAILED, client.check(id).availability());
        server.verify();
    }
}
