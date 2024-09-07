package com.vtesdecks.scheduler;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.db.VtesdleDayMapper;
import com.vtesdecks.db.model.DbVtesdleDay;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class VtesdleTodayScheduler {
    @Autowired
    private VtesdleDayMapper vtesdleDayMapper;
    @Autowired
    private CryptCache cryptCache;

    @Scheduled(cron = "${jobs.selectTodayVtesdle:1 0 0 * * *}")
    public void selectTodayVtesdle() {
        LocalDate today = LocalDate.now();
        if (vtesdleDayMapper.selectByDay(today) == null) {
            Set<Integer> cardsLastYear = vtesdleDayMapper.selectCardsLastYear();
            ResultSet<Crypt> result = cryptCache.selectAll(null, null);

            List<Integer> validCardList = result.stream()
                .map(Crypt::getId)
                .filter(cardId -> !cardsLastYear.contains(cardId))
                .collect(Collectors.toList());

            Random r = new Random();
            Integer selectedCard = validCardList.stream()
                .skip(r.nextInt(validCardList.size()))
                .findFirst().orElse(200001);

            DbVtesdleDay vtesdleDay = new DbVtesdleDay();
            vtesdleDay.setDay(today);
            vtesdleDay.setCardId(selectedCard);
            vtesdleDayMapper.insert(vtesdleDay);
        }
    }
}
