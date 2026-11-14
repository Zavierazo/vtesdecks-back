package com.vtesdecks.api.service;

import com.vtesdecks.jpa.repositories.UserMonthRepository;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class ApiPublicUserServiceTest {
    @Test
    void returnsAnEmptyPodiumWhenTheLastCompletedMonthHasNoRanking() {
        UserMonthRepository userMonthRepository = mock(UserMonthRepository.class);
        ApiPublicUserService service = new ApiPublicUserService(null, null, userMonthRepository, null, null, null);
        var monthStart = YearMonth.now().minusMonths(1).atDay(1);

        assertThat(service.getTopUsersOfMonth(null, null)).isEmpty();

        verify(userMonthRepository).findByMonthDateOrderByRankAsc(monthStart);
        verifyNoMoreInteractions(userMonthRepository);
    }
}
