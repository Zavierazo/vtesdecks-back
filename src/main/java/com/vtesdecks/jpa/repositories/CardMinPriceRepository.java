package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CardMinPriceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CardMinPriceRepository extends JpaRepository<CardMinPriceEntity, Integer> {
}
