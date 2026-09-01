package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CardShopEntity;
import com.vtesdecks.model.ShopPlatform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CardShopRepository extends JpaRepository<CardShopEntity, Integer> {

    List<CardShopEntity> findByCardId(Integer cardId);

    List<CardShopEntity> findByPlatform(ShopPlatform platform);

    List<CardShopEntity> findByPlatformIn(Collection<ShopPlatform> platform);

    List<CardShopEntity> findByCardIdAndPlatform(Integer cardId, ShopPlatform platform);

    @Query("SELECT DISTINCT cardShop.cardId FROM CardShopEntity cardShop WHERE cardShop.platform = :platform AND cardShop.inStock = true")
    List<Integer> findDistinctInStockCardIdsByPlatform(@Param("platform") ShopPlatform platform);

    CardShopEntity findByCardIdAndPlatformAndSet(Integer cardId, ShopPlatform platform, String set);

    CardShopEntity findByCardIdAndPlatformAndSetAndLocale(Integer cardId, ShopPlatform platform, String set, String locale);
}
