package com.vtesdecks.service;

import com.vtesdecks.cache.redis.entity.UserVisit;
import com.vtesdecks.cache.redis.repositories.UserVisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserVisitService {
    private final UserVisitRepository userVisitRepository;

    public LocalDate getLastVisit(Integer userId) {
        return userVisitRepository.findByUserId(userId).stream()
                .map(UserVisit::getDate)
                .filter(date -> !LocalDate.now().equals(date))
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    public void registerVisit(Integer userId) {
        try {
            UserVisit userVisit = UserVisit.builder()
                    .id(String.format("%d_%s", userId, LocalDate.now()))
                    .userId(userId)
                    .date(LocalDate.now())
                    .build();
            userVisitRepository.save(userVisit);
            deleteOldVisits(userId);
        } catch (Exception e) {
            log.error("Error registering visit for userId {}: {}", userId, e.getMessage());
        }
    }

    private void deleteOldVisits(Integer userId) {
        userVisitRepository.findByUserId(userId).stream()
                .filter(visit -> visit.getDate().isBefore(LocalDate.now().minusDays(30)))
                .forEach(visit -> {
                    try {
                        userVisitRepository.delete(visit);
                    } catch (Exception e) {
                        log.error("Error deleting old visit {} for userId {}: {}", visit.getId(), userId, e.getMessage());
                    }
                });
    }
}
