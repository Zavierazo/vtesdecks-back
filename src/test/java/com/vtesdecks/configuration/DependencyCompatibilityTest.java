package com.vtesdecks.configuration;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GooglePublicKeysManager;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.opencsv.bean.CsvToBeanBuilder;
import com.vtesdecks.api.service.ApiCollectionImportService;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.messaging.DefaultMessageDelegate;
import com.vtesdecks.messaging.configuration.RedisProducerConfiguration;
import com.vtesdecks.messaging.messages.DeckSyncData;
import com.vtesdecks.model.CardCondition;
import com.vtesdecks.model.api.ApiCollectionCardCsv;
import com.vtesdecks.util.Utils;
import io.jsonwebtoken.Jwts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DependencyCompatibilityTest {
    @Test
    void collectionCsvRoundTripsThroughProductionExporterAndConverters() throws Exception {
        ApiCollectionCardCsv card = new ApiCollectionCardCsv();
        card.setNumber(2);
        card.setCardName("Café, \"Ancient\"");
        card.setCondition(CardCondition.NM);
        card.setNotes("first line\nsecond line");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Utils.returnCsv(response, "cards.csv", ApiCollectionCardCsv.FIELDS_ORDER,
                List.of(card), ApiCollectionCardCsv.class);
        List<ApiCollectionCardCsv> restored = new CsvToBeanBuilder<ApiCollectionCardCsv>(
                new StringReader(response.getContentAsString())).withType(ApiCollectionCardCsv.class).build().parse();
        assertEquals(1, restored.size());
        assertEquals(card.getNumber(), restored.getFirst().getNumber());
        assertEquals(card.getCardName(), restored.getFirst().getCardName());
        assertEquals(card.getCondition(), restored.getFirst().getCondition());
        assertEquals(card.getNotes(), restored.getFirst().getNotes());
    }

    @Test
    void xlsxRoundTripsAndProductionImporterReadsNumericQuantities() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Cards");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Quantity");
            header.createCell(1).setCellValue("Card");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(3);
            row.createCell(1).setCellValue("Café");
            workbook.write(bytes);
        }
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes.toByteArray()))) {
            List<ApiCollectionCardCsv> cards = new ArrayList<>();
            ReflectionTestUtils.invokeMethod(ApiCollectionImportService.class, "readSheet",
                    workbook.getSheetAt(0), cards);
            assertEquals(1, cards.size());
            assertEquals(3, cards.getFirst().getNumber());
            assertEquals("Café", cards.getFirst().getCardName());
        }
    }

    @Test
    void googleVerifierChecksSignatureAudienceIssuerAndExpiryOffline() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair trusted = generator.generateKeyPair();
        GooglePublicKeysManager keys = mock(GooglePublicKeysManager.class);
        when(keys.getPublicKeys()).thenReturn(List.of(trusted.getPublic()));
        when(keys.getJsonFactory()).thenReturn(GsonFactory.getDefaultInstance());
        when(keys.getTransport()).thenReturn(mock(HttpTransport.class));
        var verifier = new GoogleIdTokenVerifier.Builder(keys).setAudience(List.of("fixture-client")).build();
        long expires = System.currentTimeMillis() + 60_000;
        assertNotNull(verifier.verify(googleToken(trusted, "fixture-client", "https://accounts.google.com", expires)));
        assertNull(verifier.verify(googleToken(trusted, "other-client", "https://accounts.google.com", expires)));
        assertNull(verifier.verify(googleToken(trusted, "fixture-client", "https://example.com", expires)));
        assertNull(verifier.verify(googleToken(trusted, "fixture-client", "https://accounts.google.com",
                System.currentTimeMillis() - 600_000)));
        assertNull(verifier.verify(googleToken(generator.generateKeyPair(), "fixture-client",
                "https://accounts.google.com", expires)));
    }

    @Test
    void redisProducerJsonRemainsCompatibleWithConsumer() {
        var template = new RedisProducerConfiguration().redisMessagingTemplate(mock(RedisConnectionFactory.class));
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) template.getValueSerializer();
        byte[] message = serializer.serialize(new DeckSyncData("fixture-deck"));
        var mapper = new WebConfiguration().jacksonBuilder().build();
        DeckIndex index = mock(DeckIndex.class);
        new DefaultMessageDelegate(index, mapper).handleMessage(new String(message, StandardCharsets.UTF_8), "deck-sync");
        verify(index).refreshIndex("fixture-deck");
    }

    @Test
    @EnabledIfSystemProperty(named = "test.redis.port", matches = "\\d+")
    void lettuceAndJacksonRoundTripAgainstIsolatedRedis() {
        var factory = new LettuceConnectionFactory("127.0.0.1", Integer.getInteger("test.redis.port"));
        factory.afterPropertiesSet();
        factory.start();
        try {
            var template = new RedisProducerConfiguration().redisMessagingTemplate(factory);
            template.afterPropertiesSet();
            template.opsForValue().set("dependency-fixture", new DeckSyncData("fixture-deck"), Duration.ofSeconds(30));
            Object result = template.opsForValue().get("dependency-fixture");
            var mapper = new WebConfiguration().jacksonBuilder().build();
            assertEquals("fixture-deck", mapper.convertValue(result, DeckSyncData.class).getDeckId());
            assertTrue(template.delete("dependency-fixture"));
        } finally {
            factory.destroy();
        }
    }

    private String googleToken(KeyPair key, String audience, String issuer, long expires) {
        return Jwts.builder().subject("fixture-user").claim("aud", audience).issuer(issuer)
                .issuedAt(new Date(System.currentTimeMillis() - 1000)).expiration(new Date(expires))
                .signWith(key.getPrivate(), Jwts.SIG.RS256).compact();
    }
}
