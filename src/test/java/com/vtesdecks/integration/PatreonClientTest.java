package com.vtesdecks.integration;

import com.sun.net.httpserver.HttpServer;
import com.vtesdecks.configuration.PatreonWebhookConfiguration;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.api.service.PatreonMembershipService;
import feign.Feign;
import feign.FeignException;
import org.springframework.cloud.openfeign.support.SpringMvcContract;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PatreonClientTest {
    @Test
    void downloadsAllPagesWithBearerAuthAndRequestedEntitlementFields() throws Exception {
        List<String> queries = new ArrayList<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/oauth2/v2/campaigns/campaign/members", exchange -> {
            assertEquals("Bearer test-token", exchange.getRequestHeaders().getFirst("Authorization"));
            queries.add(exchange.getRequestURI().getQuery());
            boolean second = queries.size() == 2;
            byte[] body = page(second ? "456" : "123", second ? null : "next-cursor", 2).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var members = client(server).members();
            assertEquals(2, members.size());
            assertEquals(PatreonMember.Decision.GRANT, members.get("123").decision());
            assertTrue(queries.getFirst().contains("currently_entitled_amount_cents"));
            assertTrue(queries.get(1).contains("page[cursor]=next-cursor"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsPartialPagesRepeatedCursorsAndMismatchingTotals() throws Exception {
        for (String response : List.of("{\"data\":[]}", page("123", "loop", 2), page("123", null, 2))) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            server.start();
            try {
                assertThrows(IllegalStateException.class, () -> client(server).members());
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void surfacesAuthenticationRateLimitAndServerFailures() throws Exception {
        for (int status : new int[]{401, 403, 429, 503}) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> { exchange.sendResponseHeaders(status, -1); exchange.close(); });
            server.start();
            try {
                var exception = assertThrows(FeignException.class, () -> client(server).members());
                assertEquals(status, exception.status());
            } finally {
                server.stop(0);
            }
        }
    }

    private PatreonMembershipService client(HttpServer server) {
        var configuration = mock(PatreonWebhookConfiguration.class);
        when(configuration.getCampaignId()).thenReturn("campaign");
        when(configuration.getAccessToken()).thenReturn("test-token");
        var options = new PatreonClient.Configuration();
        PatreonClient transport = Feign.builder().contract(new SpringMvcContract())
                .decoder((response, type) -> new ObjectMapper().readTree(response.body().asInputStream()))
                .options(options.patreonRequestOptions()).retryer(options.patreonRetryer())
                .target(PatreonClient.class, "http://127.0.0.1:" + server.getAddress().getPort());
        return new PatreonMembershipService(configuration, transport);
    }

    public static String page(String userId, String next, int total) {
        return """
                {"data":[{"type":"member","id":"member-%s","attributes":{
                "last_charge_status":"Paid","currently_entitled_amount_cents":500},
                "relationships":{"user":{"data":{"id":"%s"}},"campaign":{"data":{"id":"campaign"}}}}],
                "meta":{"pagination":{"total":%d,"cursors":{"next":%s}}}}
                """.formatted(userId, userId, total, next == null ? "null" : "\"" + next + "\"");
    }
}
