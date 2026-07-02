package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.WishlistCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WishlistCardRepository extends JpaRepository<WishlistCardEntity, Integer> {
    Optional<WishlistCardEntity> findByUserIdAndId(Integer userId, Integer id);

    List<WishlistCardEntity> findByUserIdAndCardIdAndSetAndConditionAndLanguage(Integer userId, Integer cardId, String set, String condition, String language);

    @Query("SELECT COALESCE(SUM(w.number), 0) FROM WishlistCardEntity w WHERE w.userId = :userId AND w.cardId = :cardId")
    int sumNumberByUserIdAndCardId(@Param("userId") Integer userId, @Param("cardId") Integer cardId);

    void deleteByUserIdAndId(Integer userId, Integer id);
}
