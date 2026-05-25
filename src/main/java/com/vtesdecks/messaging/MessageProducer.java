package com.vtesdecks.messaging;

import com.vtesdecks.messaging.messages.DeckSyncData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageProducer {
    private final RedisTemplate<String, Object> redisMessagingTemplate;
    private final ChannelTopic deckSyncTopic;

    public void publishDeckSync(String deckId) {
        DeckSyncData message = DeckSyncData.builder().deckId(deckId).build();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // Delay publishing until after the transaction commits so that the
            // Redis listener can find the entity in the database when it processes the message.
            log.trace("Transaction active – scheduling deck sync message after commit for deck {}", deckId);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendMessage(message);
                }
            });
        } else {
            sendMessage(message);
        }
    }

    private void sendMessage(DeckSyncData message) {
        log.trace("Publishing message to topic {}: {}", deckSyncTopic.getTopic(), message);
        redisMessagingTemplate.convertAndSend(deckSyncTopic.getTopic(), message);
    }
}