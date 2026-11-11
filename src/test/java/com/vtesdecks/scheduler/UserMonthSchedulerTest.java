package com.vtesdecks.scheduler;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.DeckIndex;
import com.vtesdecks.cache.indexable.DeckSummary;
import com.vtesdecks.cache.indexable.deck.DeckUser;
import com.vtesdecks.jpa.entity.UserMonthEntity;
import com.vtesdecks.jpa.repositories.UserMonthRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserMonthSchedulerTest {
    @Mock
    private DeckIndex deckIndex;
    @Mock
    private ResultSet<DeckSummary> decks;
    @Mock
    private UserMonthRepository userMonthRepository;
    @InjectMocks
    private UserMonthScheduler scheduler;
    @Captor
    private ArgumentCaptor<List<UserMonthEntity>> savedRankings;

    private final LocalDate monthStart = YearMonth.now().minusMonths(1).atDay(1);

    @Test
    void skipsThePreviousPodiumAndRanksTheNextEligibleUsersByScore() {
        when(userMonthRepository.findByMonthDateOrderByRankAsc(monthStart.minusMonths(1)))
                .thenReturn(List.of(priorRank(1, 1), priorRank(2, 2), priorRank(3, 3), priorRank(4, 4), priorRank(5, 5)));
        when(deckIndex.selectAll(any())).thenReturn(decks);
        when(decks.iterator()).thenReturn(List.of(
                deck(1, 90), deck(2, 80), deck(3, 70), deck(4, 20), deck(4, 25),
                deck(5, 40), deck(6, 35), deck(7, 30), deck(8, 25), deck(9, 20)).iterator());

        scheduler.selectUsersOfMonth();

        verify(userMonthRepository).saveAll(savedRankings.capture());
        assertThat(savedRankings.getValue())
                .extracting(UserMonthEntity::getUserId, UserMonthEntity::getRank, UserMonthEntity::getScore,
                        UserMonthEntity::getMonthDate)
                .containsExactly(tuple(4, 1, 45L, monthStart), tuple(5, 2, 40L, monthStart),
                        tuple(6, 3, 35L, monthStart), tuple(7, 4, 30L, monthStart), tuple(8, 5, 25L, monthStart));
    }

    @Test
    void skipsSavingWhenAllActiveUsersWereOnThePreviousPodium() {
        when(userMonthRepository.findByMonthDateOrderByRankAsc(monthStart.minusMonths(1)))
                .thenReturn(List.of(priorRank(1, 1)));
        when(deckIndex.selectAll(any())).thenReturn(decks);
        when(decks.iterator()).thenReturn(List.of(deck(1, 90)).iterator());

        scheduler.selectUsersOfMonth();

        verify(userMonthRepository, never()).saveAll(any());
    }

    private UserMonthEntity priorRank(int userId, int rank) {
        return UserMonthEntity.builder().userId(userId).rank(rank).build();
    }

    private DeckSummary deck(int userId, long views) {
        DeckSummary deck = new DeckSummary();
        deck.setUser(DeckUser.builder().id(userId).build());
        deck.setCreationDate(monthStart.atStartOfDay());
        deck.setViewsLastMonth(views);
        return deck;
    }
}
