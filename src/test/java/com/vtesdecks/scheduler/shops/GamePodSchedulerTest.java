package com.vtesdecks.scheduler.shops;

import com.sun.net.httpserver.HttpServer;
import com.vtesdecks.api.service.ApiCardService;
import com.vtesdecks.integration.GamePodClient;
import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.jpa.repositories.CardShopRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GamePodSchedulerTest {
    private final CardShopRepository repository = mock(CardShopRepository.class);
    private final GamePodScheduler scheduler = new GamePodScheduler(repository,
            mock(ApiCardService.class), mock(GamePodClient.class));

    @Test
    void onlyDeletesFinal404IncludingRedirects() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
            } else {
                String path = exchange.getRequestURI().getPath();
                int status;
                if (path.startsWith("/redirect/")) {
                    exchange.getResponseHeaders().add("Location", path.substring("/redirect".length()));
                    status = 302;
                } else {
                    status = Integer.parseInt(path.substring(1));
                }
                exchange.sendResponseHeaders(status, -1);
            }
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            ReflectionTestUtils.invokeMethod(scheduler, "cleanOutdatedCards", List.of(
                    card(1, base + "/200"), card(2, base + "/404"), card(3, base + "/500"),
                    card(4, base + "/403"), card(5, base + "/429"),
                    card(6, base + "/redirect/404"), card(7, base + "/redirect/200"),
                    card(8, "invalid uri")));
            verify(repository).deleteById(2);
            verify(repository).deleteById(6);
            verify(repository).flush();
            verifyNoMoreInteractions(repository);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void timeoutRetainsRecordAndContinuesWithNextCard() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Object> found = mock(HttpResponse.class);
        when(found.statusCode()).thenReturn(200);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpTimeoutException("test timeout")).thenReturn(found);
        scheduler.cleanOutdatedCards(List.of(card(1, "https://example.com/1"),
                card(2, "https://example.com/2")), client);
        verify(client, times(2)).send(argThat(request -> request.timeout().isPresent()), any());
        verify(repository).flush();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void interruptionRetainsRecordsAndStopsChecking() throws Exception {
        HttpClient client = mock(HttpClient.class);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new InterruptedException("test interruption"));
        try {
            scheduler.cleanOutdatedCards(List.of(card(1, "https://example.com/1"),
                    card(2, "https://example.com/2")), client);
            assertTrue(Thread.currentThread().isInterrupted());
            verify(client).send(any(HttpRequest.class), any());
            verify(repository).flush();
            verifyNoMoreInteractions(repository);
        } finally {
            Thread.interrupted();
        }
    }

    private CardShopEntity card(int id, String link) {
        return CardShopEntity.builder().id(id).cardId(id).link(link).build();
    }
}
