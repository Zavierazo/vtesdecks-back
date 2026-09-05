package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.UserAchievementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserAchievementRepository extends JpaRepository<UserAchievementEntity, Long> {
    List<UserAchievementEntity> findByUserIdOrderByEarnedDateAsc(Integer userId);
}
