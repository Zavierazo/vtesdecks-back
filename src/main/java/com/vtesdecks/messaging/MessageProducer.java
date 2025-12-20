package com.vtesdecks.messaging;

import com.vtesdecks.messaging.messages.DeckSyncData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageProducer {
    private final RedisTemplate<String, Object> redisMessagingTemplate;
    private final ChannelTopic deckSyncTopic;

    public void publishDeckSync(String deckId) {
        DeckSyncData message = DeckSyncData.builder().deckId(deckId).build();
        log.trace("Publishing message to topic {}: {}", deckSyncTopic.getTopic(), message);
        redisMessagingTemplate.convertAndSend(deckSyncTopic.getTopic(), message);
    }
}