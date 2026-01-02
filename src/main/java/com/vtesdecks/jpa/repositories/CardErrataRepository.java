package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CardErrataEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CardErrataRepository extends JpaRepository<CardErrataEntity, Integer> {

    List<CardErrataEntity> findByCardId(Integer cardId);

    List<CardErrataEntity> findByEffectiveDateAfterAndRequiresWarningTrueAndCardIdIn(LocalDate effectiveDate, List<Integer> cardIds);
}

