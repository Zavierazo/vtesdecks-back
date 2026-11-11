package com.vtesdecks.api.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vtesdecks.api.util.ApiUtils;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.DeckEntity;
import com.vtesdecks.jpa.repositories.DeckRepository;
import com.vtesdecks.jpa.repositories.DeckCardRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.model.api.ApiDeckBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class BuilderPublicationWarningTest {
    @Mock DeckRepository decks;
    @Mock DeckCardRepository cards;
    @Mock MessageProducer messages;
    @Mock ApiUserNotificationService notifications;
    @Mock AchievementService achievements;
    @Mock DeckValidation validator;
    @InjectMocks ApiDeckBuilderService builder;

    @Test void invalidPublicSaveWarnsButPreservesPublicationAndSideEffects() {
        when(validator.isValid(any(), any(), any())).thenAnswer(invocation -> {
            assertEquals("Deck", invocation.getArgument(0));
            assertEquals("Previous name", decks.findById("owned").orElseThrow().getName());
            verify(decks, never()).save(any());
            verifyNoInteractions(cards, messages, notifications, achievements);
            return false;
        });
        checkSave(true, DeckType.COMMUNITY, "validation failed");
        verify(validator).isValid(eq("Deck"), isNull(), eq(List.of()));
        verify(notifications).deckUpdateNotifications(any());
    }

    @Test void validationFailureDoesNotBlockSave() {
        when(validator.isValid(any(), any(), any())).thenThrow(new IllegalStateException("catalog unavailable"));
        checkSave(true, DeckType.COMMUNITY, "validation unavailable");
    }

    @Test void validPublicSaveDoesNotWarn() {
        when(validator.isValid(any(), any(), any())).thenReturn(true);
        checkSave(true, DeckType.COMMUNITY, null);
    }

    @Test void privateDraftsAndPreconstructedDecksSkipDiagnostic() {
        checkSave(false, DeckType.COMMUNITY, null);
        checkSave(true, DeckType.PRECONSTRUCTED, null);
        verifyNoInteractions(validator);
    }

    @ParameterizedTest
    @CsvSource({"true,false", "false,false", "true,true"})
    void deckAchievementEvaluationRequiresCommitAndPreservesCommittedSaves(boolean committed, boolean evaluationFails) {
        DeckEntity deck = DeckEntity.builder().id("owned").user(7).name("Previous name")
                .type(DeckType.COMMUNITY).published(false).collection(false).build();
        when(decks.findById("owned")).thenReturn(Optional.of(deck));
        when(cards.findByIdDeckId("owned")).thenReturn(List.of());
        var input = new ApiDeckBuilder();
        input.setId("owned");
        input.setName("Deck");
        input.setPublished(true);
        input.setCards(new ArrayList<>());
        var extra = JsonNodeFactory.instance.objectNode();
        extra.putObject("advent").put("year", 2026).put("day", 1);
        input.setExtra(extra);
        when(validator.isValid(eq("Deck"), eq(extra), anyList())).thenReturn(true);
        if (evaluationFails) {
            doThrow(new IllegalStateException("Achievement service unavailable")).when(achievements).activity(7);
        }

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try (var auth = mockStatic(ApiUtils.class)) {
            auth.when(ApiUtils::extractUserId).thenReturn(7);
            builder.storeDeck(input);
            verify(achievements, never()).activity(7);
            verify(achievements, never()).awardAdventChallenges(any());
            if (committed) {
                assertDoesNotThrow(TransactionSynchronizationUtils::triggerAfterCommit);
            }
            TransactionSynchronizationUtils.triggerAfterCompletion(committed
                    ? TransactionSynchronization.STATUS_COMMITTED : TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(achievements, times(committed ? 1 : 0)).activity(7);
            verify(achievements, times(committed && !evaluationFails ? 1 : 0)).awardAdventChallenges(7);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    private void checkSave(boolean published, DeckType type, String expectedWarning) {
        DeckEntity deck = DeckEntity.builder().id("owned").user(7).name("Previous name")
                .type(type).published(false).collection(false).build();
        when(decks.findById("owned")).thenReturn(Optional.of(deck));
        when(cards.findByIdDeckId("owned")).thenReturn(List.of());
        ApiDeckBuilder input = new ApiDeckBuilder();
        input.setId("owned"); input.setName("Deck"); input.setPublished(published);
        input.setCards(new ArrayList<>());
        Logger logger = (Logger) LoggerFactory.getLogger(ApiDeckBuilderService.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start(); logger.addAppender(logs);
        try (var auth = mockStatic(ApiUtils.class)) {
            auth.when(ApiUtils::extractUserId).thenReturn(7);
            assertEquals(published, builder.storeDeck(input).isPublished());
            verify(decks, atLeastOnce()).save(deck);
            verify(messages, atLeastOnce()).publishDeckSync("owned");
            verify(achievements, atLeastOnce()).activity(7);
            var warnings = logs.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
            if (expectedWarning == null) {
                assertTrue(warnings.isEmpty());
            } else {
                assertEquals(1, warnings.size());
                String warning = warnings.getFirst().getFormattedMessage();
                assertTrue(warning.contains(expectedWarning));
                assertTrue(warning.contains("owned"));
                assertTrue(warning.contains("warning-only, save allowed"));
            }
        } finally {
            logger.detachAppender(logs); logs.stop();
        }
    }
}
