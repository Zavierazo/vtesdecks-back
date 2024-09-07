package com.vtesdecks.scheduler;

import com.vtesdecks.db.DeckCardMapper;
import com.vtesdecks.db.DeckMapper;
import com.vtesdecks.db.DeckUserMapper;
import com.vtesdecks.db.DeckViewMapper;
import com.vtesdecks.db.model.DbDeck;
import com.vtesdecks.db.model.DbDeckView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CleanUpScheduler {
    @Autowired
    private DeckMapper deckMapper;
    @Autowired
    private DeckViewMapper deckViewMapper;
    @Autowired
    private DeckCardMapper deckCardMapper;
    @Autowired
    private DeckUserMapper deckUserMapper;

    @Scheduled(cron = "${jobs.deckViewCleanCron:0 0 2 * * *}")
    public void deckViewCleanScheduler() {
        List<DbDeckView> views = deckViewMapper.selectOld();
        if (!CollectionUtils.isEmpty(views)) {
            Map<String, Integer> deckViews = new HashMap<>();
            //Collect views
            for (DbDeckView view : views) {
                String deckId = view.getDeckId();
                deckViews.put(deckId, deckViews.getOrDefault(deckId, 0) + 1);
            }
            //Update deck views
            for (Map.Entry<String, Integer> deckViewEntry : deckViews.entrySet()) {
                DbDeck deck = deckMapper.selectById(deckViewEntry.getKey());
                if (deck != null) {
                    deck.setViews(deck.getViews() + deckViewEntry.getValue());
                    deckMapper.update(deck);
                }
            }
            //Remove old views
            for (DbDeckView view : views) {
                deckViewMapper.delete(view.getId(), view.getDeckId());
            }
            log.info("Cleaned {} deck views!", views.size());
        }
    }

    @Scheduled(cron = "${jobs.deckCleanCron:0 0 1 * * *}")
    public void deckCleanScheduler() {
        List<DbDeck> decksToDelete = deckMapper.selectOldDeleted();
        if (!CollectionUtils.isEmpty(decksToDelete)) {
            for (DbDeck deck : decksToDelete) {
                log.warn("Deleting deck forever: {}", deck);
                deckViewMapper.deleteByDeckId(deck.getId());
                deckCardMapper.deleteByDeckId(deck.getId());
                deckUserMapper.deleteByDeckId(deck.getId());
                deckMapper.delete(deck.getId());
            }
        }

    }
}
