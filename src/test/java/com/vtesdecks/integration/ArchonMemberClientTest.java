package com.vtesdecks.integration;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real HTTP transport against the documented Member API contract. */
class ArchonMemberClientTest {
    @Test
    void sendsPkceCredentialsAndBearerAuthAndRevokesBothTokens() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> forms = new ArrayList<>();
        server.createContext("/oauth/token", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            assertTrue(exchange.getRequestHeaders().getFirst("Content-Type").startsWith("application/x-www-form-urlencoded"));
            forms.add(URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            byte[] body = "{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"token_type\":\"Bearer\",\"scope\":\"profile:read\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/oauth/userinfo", exchange -> {
            assertEquals("Bearer access", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "{\"sub\":\"member\",\"vekn_id\":\"003120065\",\"roles\":[\"Prince\"]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/oauth/revoke", exchange -> {
            forms.add(URLDecoder.decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = client(server);
            var tokens = client.exchange("code+with symbols", "verifier");
            assertEquals("003120065", client.userInfo(tokens.accessToken()).veknId());
            client.revoke(tokens.refreshToken());
            client.revoke(tokens.accessToken());
            assertEquals(3, forms.size());
            assertTrue(forms.getFirst().contains("code=code+with symbols"));
            assertTrue(forms.getFirst().contains("code_verifier=verifier"));
            assertTrue(forms.getFirst().contains("redirect_uri=https://vtesdecks.com/user/archon/callback"));
            assertTrue(forms.getFirst().contains("client_secret=test-secret"));
            assertTrue(forms.get(1).contains("token=refresh"));
            assertTrue(forms.get(2).contains("token=access"));
        } finally { server.stop(0); }
    }

    @Test
    void doesNotReplayRateLimitedOrFailingCodeExchanges() throws Exception {
        for (int status : new int[]{429, 503}) {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var calls = new java.util.concurrent.atomic.AtomicInteger();
            server.createContext("/oauth/token", exchange -> {
                calls.incrementAndGet();
                exchange.getResponseHeaders().set("Retry-After", "0");
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
            });
            server.start();
            try {
                assertThrows(RestClientResponseException.class, () -> client(server).exchange("code", "verifier"));
                assertEquals(1, calls.get());
            } finally { server.stop(0); }
        }
    }

    @Test
    void requestsOnlyProfileReadWithTheExactCallbackAndS256() {
        var client = new ArchonMemberClient("client", "secret", "https://vtesdecks.com/user/archon/callback", "https://archon.vekn.net");
        String url = URLDecoder.decode(client.authorizationUrl("state", "challenge"), StandardCharsets.UTF_8);
        assertTrue(url.startsWith("https://archon.vekn.net/consent?"));
        assertTrue(url.contains("scope=profile:read"));
        assertTrue(url.contains("redirect_uri=https://vtesdecks.com/user/archon/callback"));
        assertTrue(url.contains("code_challenge_method=S256"));
        assertFalse(url.contains("secret"));
        assertFalse(url.contains("profile:email"));
        assertFalse(new ArchonMemberClient("client", "secret", "", "https://archon.vekn.net").available());
    }

    private ArchonMemberClient client(HttpServer server) {
        return new ArchonMemberClient("test-client", "test-secret", "https://vtesdecks.com/user/archon/callback",
                "http://127.0.0.1:" + server.getAddress().getPort());
    }
}
