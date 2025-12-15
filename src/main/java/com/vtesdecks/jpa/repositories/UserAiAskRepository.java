package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.UserAiAskEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserAiAskRepository extends JpaRepository<UserAiAskEntity, Integer> {

    @Query(value = "SELECT count(1) FROM user_ai_ask WHERE user = :user AND  creation_date > (NOW() - INTERVAL 3600 SECOND)", nativeQuery = true)
    Integer selectLastByUser(String user);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM user_ai_ask WHERE creation_date < (NOW() - INTERVAL 86400 SECOND)", nativeQuery = true)
    void deleteOld();
}