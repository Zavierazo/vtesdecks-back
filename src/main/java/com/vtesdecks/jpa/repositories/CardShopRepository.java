package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CardShopEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardShopRepository extends JpaRepository<CardShopEntity, Integer> {

    List<CardShopEntity> findByCardId(Integer cardId);

    List<CardShopEntity> findByPlatform(String platform);

    List<CardShopEntity> findByCardIdAndPlatform(Integer cardId, String platform);
}