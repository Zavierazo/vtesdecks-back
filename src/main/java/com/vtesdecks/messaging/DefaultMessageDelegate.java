package com.vtesdecks.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.messaging.messages.DeckSyncData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultMessageDelegate implements MessageDelegate {
    private final DeckIndex deckIndex;
    private final ObjectMapper objectMapper;

    @Override
    public void handleMessage(String message, String channel) {
        log.info("Received message for channel {}: {}", channel, message);
        try {
            switch (channel) {
                case "deck-sync" -> handleMessage(objectMapper.readValue(message, DeckSyncData.class));
            }
        } catch (Exception e) {
            log.error("Error handling message for channel {}: {}", channel, message, e);
        }
    }

    private void handleMessage(DeckSyncData message) {
        deckIndex.refreshIndex(message.getDeckId());
    }


}