package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.model.ShopPlatform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardShopRepository extends JpaRepository<CardShopEntity, Integer> {

    List<CardShopEntity> findByCardId(Integer cardId);

    List<CardShopEntity> findByPlatform(ShopPlatform platform);

    List<CardShopEntity> findByCardIdAndPlatform(Integer cardId, ShopPlatform platform);

    CardShopEntity findByCardIdAndPlatformAndSet(Integer cardId, ShopPlatform platform, String set);
}