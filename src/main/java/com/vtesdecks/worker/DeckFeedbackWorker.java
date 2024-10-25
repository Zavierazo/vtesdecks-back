package com.vtesdecks.worker;

import com.google.common.hash.Hashing;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.db.DeckMapper;
import com.vtesdecks.db.DeckViewMapper;
import com.vtesdecks.db.model.DbDeckView;
import com.vtesdecks.db.model.DbUser;
import com.vtesdecks.util.Utils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@Log4j2
public class DeckFeedbackWorker implements Runnable {

    private final BlockingQueue<DeckFeedback> operationLogQueue = new LinkedBlockingQueue<>();
    private boolean keepRunning = true;
    @Autowired
    private DeckMapper deckMapper;
    @Autowired
    private DeckIndex deckIndex;
    @Autowired
    private DeckViewMapper deckViewMapper;

    @Data
    @Builder
    public static class DeckFeedback {
        private String ip;
        private String user;
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
                if (deckFeedback.getType() == DeckFeedback.FeedbackType.VIEW) {
                    String voteId = deckFeedback.getIp() + (deckFeedback.getUserAgent() != null ? deckFeedback.getUserAgent() : "");
                    DbDeckView deckView = new DbDeckView();
                    deckView.setId(Hashing.sha256()
                            .hashString(voteId, StandardCharsets.UTF_8)
                            .toString());
                    deckView.setDeckId(deckFeedback.getDeck());
                    deckView.setSource(deckFeedback.getSource());
                    try {
                        deckViewMapper.insert(deckView);
                        log.debug("View for deck {} with {}:{}:{}", deckFeedback.getDeck(), deckFeedback.getUser(), voteId, deckView.getId());
                    } catch (DuplicateKeyException duplicateKeyException) {
                        log.debug("Duplicated view for deck {} with {}:{}:{}", deckFeedback.getDeck(), deckFeedback.getUser(), voteId,
                                deckView.getId());
                    }
                }
                deckIndex.enqueueRefreshIndex(deckFeedback.getDeck());
            } catch (final InterruptedException e) {
                log.error("Blocking queue was interrupted {}", e);
                // Restore interrupted state...
                Thread.currentThread().interrupt();
            } catch (final Exception e) {
                log.error("Unhandled exception: {} ", e);
            }
        }
    }

    public void enqueueView(String deck, DbUser user, String source, HttpServletRequest httpServletRequest) {
        operationLogQueue
                .add(DeckFeedback.builder()
                        .deck(deck)
                        .user(user != null ? user.getUsername() : null)
                        .ip(Utils.getIp(httpServletRequest))
                        .userAgent(httpServletRequest.getHeader("User-Agent"))
                        .type(DeckFeedback.FeedbackType.VIEW)
                        .source(source)
                        .increment(1)
                        .build());
    }
}
