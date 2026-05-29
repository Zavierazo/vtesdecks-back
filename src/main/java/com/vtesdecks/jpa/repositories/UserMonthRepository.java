package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.UserMonthEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface UserMonthRepository extends JpaRepository<UserMonthEntity, Integer> {

    List<UserMonthEntity> findByMonthDateOrderByRankAsc(LocalDate monthDate);

    boolean existsByMonthDate(LocalDate monthDate);

    @Query(value = "SELECT * FROM user_month WHERE month_date = (SELECT MAX(month_date) FROM user_month) ORDER BY `rank` ASC LIMIT :limit", nativeQuery = true)
    List<UserMonthEntity> findLatestMonthTop(int limit);
}


