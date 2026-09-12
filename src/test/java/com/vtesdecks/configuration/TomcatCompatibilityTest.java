package com.vtesdecks.configuration;

import com.vtesdecks.api.service.UserSecurityService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TomcatCompatibilityTest {
    private static final String KEY = "a".repeat(64);
    private static ServletWebServerApplicationContext context;
    private static HttpClient client;
    private static Path uploads;
    private static String base;

    @BeforeAll
    static void startIsolatedServer() throws Exception {
        uploads = Files.createTempDirectory("vtesdecks-tomcat-test-");
        Properties production = new Properties();
        try (var input = TomcatCompatibilityTest.class.getResourceAsStream("/application.properties")) {
            production.load(input);
        }
        Map<String, Object> settings = new HashMap<>();
        production.forEach((key, value) -> {
            String name = key.toString();
            if (name.startsWith("server.") || name.startsWith("spring.servlet.multipart.")
                    || name.startsWith("spring.mvc.")) {
                settings.put(name, value);
            }
        });
        // Load only the web stack: no component scan, database, jobs, or production secrets.
        settings.put("spring.config.location", "optional:classpath:/dependency-test.properties");
        settings.put("server.port", "0");
        settings.put("server.address", "127.0.0.1");
        settings.put("spring.servlet.multipart.location", uploads.toString());
        settings.put("jwt.secret", KEY);
        settings.put("jwt.reject-legacy-tokens", "true");
        SpringApplication application = new SpringApplication(WebOnly.class);
        application.setDefaultProperties(settings);
        context = (ServletWebServerApplicationContext) application.run();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        base = "http://127.0.0.1:" + context.getWebServer().getPort();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (client != null) {
            client.close();
        }
        if (context != null) {
            context.close();
        }
        if (uploads != null) {
            try (var files = Files.list(uploads)) {
                for (Path file : files.toList()) {
                    Files.delete(file);
                }
            }
            Files.delete(uploads);
        }
    }

    @Test
    void usesTomcatWithExistingUploadLimits() {
        assertInstanceOf(TomcatWebServer.class, context.getWebServer());
        MultipartConfigElement config = context.getBean(MultipartConfigElement.class);
        assertEquals(50L * 1024 * 1024, config.getMaxFileSize());
        assertEquals(75L * 1024 * 1024, config.getMaxRequestSize());
        assertEquals(2048, config.getFileSizeThreshold());
    }

    @Test
    void preservesPublicRoutingRawJwtAuthenticationAndCors() throws Exception {
        assertEquals(200, send("/api/1.0/probe", null).statusCode());
        assertEquals(403, send("/api/1.0/user/probe", null).statusCode());
        assertEquals(403, send("/api/1.0/user/probe", "invalid").statusCode());
        assertEquals(200, send("/api/1.0/user/probe", token()).statusCode());
        var request = HttpRequest.newBuilder(URI.create(base + "/api/1.0/probe"))
                .header("Origin", "https://example.com")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    }

    @Test
    void acceptsAuthenticatedMultipartAndRejectsOversizedFile() throws Exception {
        String contents = "name,number\nCafé,2\n";
        var response = upload(contents.getBytes(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode());
        assertEquals(contents, response.body());
        assertEquals(413, upload(new byte[50 * 1024 * 1024 + 1]).statusCode());
    }

    private HttpResponse<String> upload(byte[] contents) throws Exception {
        var body = HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofString("--fixture\r\nContent-Disposition: form-data; name=\"file\"; filename=\"cards.csv\"\r\nContent-Type: text/csv\r\n\r\n"),
                HttpRequest.BodyPublishers.ofByteArray(contents),
                HttpRequest.BodyPublishers.ofString("\r\n--fixture--\r\n"));
        var request = HttpRequest.newBuilder(URI.create(base + "/api/1.0/user/upload"))
                .timeout(Duration.ofSeconds(30)).header("Authorization", token())
                .header("Content-Type", "multipart/form-data; boundary=fixture").POST(body).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String path, String jwt) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10));
        if (jwt != null) {
            request.header("Authorization", jwt);
        }
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private String token() {
        return Jwts.builder().subject("42").claim("token_use", "access").claim("auth_version", 0)
                .issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(KEY.getBytes(StandardCharsets.UTF_8))).compact();
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({PropertyPlaceholderAutoConfiguration.class,
            ServletWebServerFactoryAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class,
            JacksonAutoConfiguration.class, MultipartAutoConfiguration.class,
            SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
    @Import({ApiSecurityConfiguration.class, WebConfiguration.class, ProbeController.class})
    static class WebOnly {
        @Bean
        UserSecurityService userSecurityService() {
            UserSecurityService service = mock(UserSecurityService.class);
            when(service.authenticate(any(Claims.class), eq(true))).thenReturn(
                    new UsernamePasswordAuthenticationToken("42", null,
                            List.of(new SimpleGrantedAuthority("USER"))));
            return service;
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/api/1.0/probe", "/api/1.0/user/probe"})
        String probe() {
            return "ok";
        }

        @PostMapping("/api/1.0/user/upload")
        String upload(@RequestParam("file") MultipartFile file) throws Exception {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        }
    }
}
