package com.vtesdecks.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.Library;
import com.vtesdecks.cache.redis.repositories.DeckRedisRepository;
import com.vtesdecks.model.DeckExportType;
import com.vtesdecks.service.DeckService;
import com.vtesdecks.service.impl.DeckExportServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.convert.MappingRedisConverter;
import org.springframework.data.redis.core.convert.RedisData;
import org.springframework.data.redis.core.mapping.RedisMappingContext;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class DeckRedisRepositoryTest {
    @Configuration
    @EnableRedisRepositories(basePackageClasses = DeckRedisRepository.class)
    static class RepositoryConfiguration {
    }
    @Test
    void repositoryMappingPreservesFullDeckAndAdventJson() throws Exception {
        var converter = new MappingRedisConverter(
                new RedisMappingContext());
        converter.afterPropertiesSet();
        Deck deck = DeckCacheFixtures.deck();
        deck.setCacheTtl(900L);
        RedisData data = new RedisData();
        converter.write(deck, data);
        Deck restored = converter.read(Deck.class, data);
        assertEquals(deck, restored);
        assertEquals(deck.getId(), data.getId());
        assertEquals(900L, data.getTimeToLive());
        assertEquals(deck.getExtra(), restored.getExtra());
        assertEquals(deck.getStats().getClass(), restored.getStats().getClass());
    }

    @ParameterizedTest
    @EnumSource(DeckExportType.class)
    void allExportsMatchAfterRepositoryMapping(DeckExportType type) throws Exception {
        Deck deck = DeckCacheFixtures.deck();
        var converter = new MappingRedisConverter(
                new RedisMappingContext());
        converter.afterPropertiesSet();
        RedisData data = new RedisData();
        converter.write(deck, data);
        Deck restored = converter.read(Deck.class, data);
        var service = new DeckExportServiceImpl();
        var decks = mock(DeckService.class);
        var crypts = mock(CryptCache.class);
        var libraries = mock(LibraryCache.class);
        var crypt = new Crypt();
        crypt.setName("Fixture vampire");
        crypt.setCapacity(5);
        crypt.setGroup(5);
        var library = new Library();
        library.setName("Fixture library card");
        library.setType("Action/Combat");
        Mockito.when(crypts.get(200001)).thenReturn(crypt);
        Mockito.when(libraries.get(100001)).thenReturn(library);
        ReflectionTestUtils.setField(service, "deckService", decks);
        ReflectionTestUtils.setField(service, "cryptCache", crypts);
        ReflectionTestUtils.setField(service, "libraryCache", libraries);
        Mockito.when(decks.getDeck(deck.getId())).thenReturn(deck, restored);
        String original = service.export(type, deck.getId());
        assertEquals(original, service.export(type, deck.getId()));
        assertTrue(original.contains("Fixture library card"));
        assertTrue(original.contains("Fixture vampire"));
    }

    @Test
    @EnabledIfSystemProperty(named = "test.redis.port", matches = "\\d+")
    void standardRepositoryRoundTripsExpiresAndDeletes() throws Exception {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean("redisConnectionFactory", RedisConnectionFactory.class, () -> {
                var factory = new LettuceConnectionFactory("127.0.0.1", Integer.getInteger("test.redis.port"));
                factory.afterPropertiesSet();
                return factory;
            });
            // Infrastructure used by the other normal repositories in the application.
            context.registerBean("redisTemplate", RedisTemplate.class, () -> {
                RedisTemplate<String, Object> template = new RedisTemplate<>();
                template.setConnectionFactory(context.getBean(RedisConnectionFactory.class));
                return template;
            });
            context.register(RepositoryConfiguration.class);
            context.refresh();
            DeckRedisRepository repository = context.getBean(DeckRedisRepository.class);
            Deck deck = DeckCacheFixtures.deck();
            deck.setId("test:" + UUID.randomUUID());
            deck.setCacheTtl(1L);
            try {
                repository.save(deck);
                assertEquals(deck, repository.findById(deck.getId()).orElseThrow());
                long deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
                while (repository.findById(deck.getId()).isPresent() && System.nanoTime() < deadline) {
                    Thread.sleep(50);
                }
                assertTrue(repository.findById(deck.getId()).isEmpty());
                deck.setCacheTtl(900L);
                repository.save(deck);
                Deck cached = repository.findById(deck.getId()).orElseThrow();
                deadline = System.nanoTime() + Duration.ofSeconds(4).toNanos();
                while (cached.getCacheTtl() >= 900 && System.nanoTime() < deadline) {
                    Thread.sleep(50);
                    cached = repository.findById(deck.getId()).orElseThrow();
                }
                long remainingTtl = cached.getCacheTtl();
                assertTrue(remainingTtl > 0 && remainingTtl < 900);
                cached.setDescription("Refreshed without renewing expiry");
                repository.save(cached);
                Deck updated = repository.findById(deck.getId()).orElseThrow();
                assertEquals(cached.getDescription(), updated.getDescription());
                assertTrue(updated.getCacheTtl() > 0 && updated.getCacheTtl() <= remainingTtl);
                repository.deleteById(deck.getId());
                assertTrue(repository.findById(deck.getId()).isEmpty());
            } finally {
                repository.deleteById(deck.getId());
            }
        }
    }
}
