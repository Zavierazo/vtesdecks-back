package com.vtesdecks.scheduler;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.indexable.Deck;
import com.vtesdecks.cache.indexable.deck.DeckType;
import com.vtesdecks.jpa.entity.UserMonthEntity;
import com.vtesdecks.jpa.repositories.UserMonthRepository;
import com.vtesdecks.model.DeckQuery;
import com.vtesdecks.model.DeckSort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserMonthScheduler {

    private static final int TOP_SIZE = 5;

    private final DeckIndex deckIndex;
    private final UserMonthRepository userMonthRepository;

    @Scheduled(cron = "${jobs.userMonthCron:0 0 6 * * *}")
    @Transactional
    public void selectUsersOfMonth() {
        YearMonth previousMonth = YearMonth.now().minusMonths(1);
        LocalDate monthStart = previousMonth.atDay(1);

        if (userMonthRepository.existsByMonthDate(monthStart)) {
            log.debug("Users of month for {} already calculated, skipping.", monthStart);
            return;
        }

        log.info("Calculating top {} users of month for {}", TOP_SIZE, monthStart);

        LocalDateTime monthEnd = previousMonth.atEndOfMonth().atTime(23, 59, 59);

        DeckQuery query = DeckQuery.builder()
                .type(DeckType.COMMUNITY)
                .order(DeckSort.NEWEST)
                .creationDate(monthStart)
                .build();

        Map<Integer, Long> scoreByUser = new HashMap<>();
        try (ResultSet<Deck> result = deckIndex.selectAll(query)) {
            for (Deck deck : result) {
                if (deck.getUser() == null || deck.getCreationDate() == null) {
                    continue;
                }
                // creationDate filter in DeckQuery only applies >= monthStart;
                // we also need to exclude decks from the current month onwards
                if (deck.getCreationDate().isAfter(monthEnd)) {
                    continue;
                }
                long views = deck.getViewsLastMonth() != null ? deck.getViewsLastMonth() : 0L;
                scoreByUser.merge(deck.getUser().getId(), views, Long::sum);
            }
        }

        if (scoreByUser.isEmpty()) {
            log.info("No community decks found for {}, no users of month saved.", monthStart);
            return;
        }

        List<Map.Entry<Integer, Long>> sorted = new ArrayList<>(scoreByUser.entrySet());
        sorted.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        List<UserMonthEntity> toSave = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<Integer, Long> entry : sorted) {
            if (rank > TOP_SIZE) {
                break;
            }
            toSave.add(UserMonthEntity.builder()
                    .userId(entry.getKey())
                    .monthDate(monthStart)
                    .rank(rank)
                    .score(entry.getValue())
                    .build());
            rank++;
        }

        userMonthRepository.saveAll(toSave);
        userMonthRepository.flush();
        log.info("Saved {} users of month for {}", toSave.size(), monthStart);
    }
}



