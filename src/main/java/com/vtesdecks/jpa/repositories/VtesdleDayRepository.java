package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.VtesdleDayEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Set;

public interface VtesdleDayRepository extends JpaRepository<VtesdleDayEntity, LocalDate> {

    @Query(value = "SELECT card_id FROM vtesdle_day WHERE day >= current_date - interval '1' year", nativeQuery = true)
    Set<Integer> selectCardsLastYear();

}