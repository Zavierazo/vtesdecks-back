package com.vtesdecks.worker;

import com.google.common.hash.Hashing;
import com.vtesdecks.jpa.entity.DeckViewEntity;
import com.vtesdecks.jpa.repositories.DeckUserRepository;
import com.vtesdecks.jpa.repositories.DeckViewRepository;
import com.vtesdecks.messaging.MessageProducer;
import com.vtesdecks.util.Utils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Log4j2
@RequiredArgsConstructor
public class DeckFeedbackWorker implements Runnable {
    private final BlockingQueue<DeckFeedback> operationLogQueue = new LinkedBlockingQueue<>();
    private final MessageProducer messageProducer;
    private final DeckViewRepository deckViewRepository;
    private final DeckUserRepository deckUserRepository;
    private boolean keepRunning = true;

    @Data
    @Builder
    public static class DeckFeedback {
        private String ip;
        private Integer userId;
        private String userAgent;
        private String deck;
        private FeedbackType type;
        private String source;
        private int increment;

        public enum FeedbackType {
            VIEW
        }
    }

    @PostConstruct
    private void initialise() {
        Thread loggingWorkerThread = new Thread(this);
        loggingWorkerThread.setDaemon(true);
        loggingWorkerThread.start();
    }

    @Override
    public void run() {
        while (keepRunning) {
            try {
                DeckFeedback deckFeedback = operationLogQueue.take();
                process(deckFeedback);
            } catch (final InterruptedException e) {
                log.error("Blocking queue was interrupted {}", e);
                // Restore interrupted state...
                Thread.currentThread().interrupt();
            } catch (final Exception e) {
                log.error("Unhandled exception: {} ", e);
            }
        }
    }

    void process(DeckFeedback deckFeedback) {
        if (deckFeedback.getType() == DeckFeedback.FeedbackType.VIEW) {
            String voteId = deckFeedback.getIp() + (deckFeedback.getUserAgent() != null ? deckFeedback.getUserAgent() : "");
            DeckViewEntity deckView = new DeckViewEntity();
            deckView.setId(new DeckViewEntity.DeckViewId());
            deckView.getId().setId(Hashing.sha256()
                    .hashString(voteId, StandardCharsets.UTF_8)
                    .toString());
            deckView.getId().setDeckId(deckFeedback.getDeck());
            deckView.setSource(deckFeedback.getSource());
            deckViewRepository.saveAndFlush(deckView);
            if (deckFeedback.getUserId() != null) {
                deckUserRepository.recordVisit(deckFeedback.getUserId(), deckFeedback.getDeck());
            }
        }
        messageProducer.publishDeckSync(deckFeedback.getDeck());
    }

    public void enqueueView(String deck, Integer userId, String source, HttpServletRequest httpServletRequest) {
        operationLogQueue
                .add(DeckFeedback.builder()
                        .deck(deck)
                        .userId(userId)
                        .ip(Utils.getIp(httpServletRequest))
                        .userAgent(httpServletRequest.getHeader("User-Agent"))
                        .type(DeckFeedback.FeedbackType.VIEW)
                        .source(source)
                        .increment(1)
                        .build());
    }
}
