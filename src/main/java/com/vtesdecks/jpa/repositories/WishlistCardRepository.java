package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.WishlistCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WishlistCardRepository extends JpaRepository<WishlistCardEntity, Integer> {
    Optional<WishlistCardEntity> findByUserIdAndId(Integer userId, Integer id);

    List<WishlistCardEntity> findByUserIdAndCardIdAndSetAndConditionAndLanguage(Integer userId, Integer cardId, String set, String condition, String language);

    void deleteByUserIdAndId(Integer userId, Integer id);
}
