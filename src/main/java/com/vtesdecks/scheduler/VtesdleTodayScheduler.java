package com.vtesdecks.scheduler;

import com.googlecode.cqengine.resultset.ResultSet;
import com.vtesdecks.cache.CryptCache;
import com.vtesdecks.cache.indexable.Crypt;
import com.vtesdecks.jpa.entity.VtesdleDayEntity;
import com.vtesdecks.jpa.repositories.VtesdleDayRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class VtesdleTodayScheduler {
    @Autowired
    private VtesdleDayRepository vtesdleDayRepository;
    @Autowired
    private CryptCache cryptCache;

    @Scheduled(cron = "${jobs.selectTodayVtesdle:1 0 0 * * *}")
    public void selectTodayVtesdle() {
        LocalDate today = LocalDate.now();
        if (vtesdleDayRepository.findById(today).isEmpty()) {
            Set<Integer> cardsLastYear = vtesdleDayRepository.selectCardsLastYear();
            ResultSet<Crypt> result = cryptCache.selectAll(null, null);

            List<Integer> validCardList = result.stream()
                    .map(Crypt::getId)
                    .filter(cardId -> !cardsLastYear.contains(cardId))
                    .collect(Collectors.toList());

            Random r = new Random();
            Integer selectedCard = validCardList.stream()
                    .skip(r.nextInt(validCardList.size()))
                    .findFirst().orElse(200001);

            VtesdleDayEntity vtesdleDay = new VtesdleDayEntity();
            vtesdleDay.setDay(today);
            vtesdleDay.setCardId(selectedCard);
            vtesdleDayRepository.saveAndFlush(vtesdleDay);
        }
    }
}
