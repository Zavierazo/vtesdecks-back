package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.UserMonthEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface UserMonthRepository extends JpaRepository<UserMonthEntity, Integer> {

    List<UserMonthEntity> findByMonthDateOrderByRankAsc(LocalDate monthDate);

    boolean existsByMonthDate(LocalDate monthDate);
}


