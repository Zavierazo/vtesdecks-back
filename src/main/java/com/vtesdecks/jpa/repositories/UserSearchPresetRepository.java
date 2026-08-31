package com.vtesdecks.jpa.repositories;

import com.vtesdecks.enums.SearchPresetScope;
import com.vtesdecks.jpa.entity.UserSearchPresetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserSearchPresetRepository extends JpaRepository<UserSearchPresetEntity, Integer> {
    List<UserSearchPresetEntity> findByUserIdOrderByScopeAscNameAsc(Integer userId);

    Optional<UserSearchPresetEntity> findByUserIdAndId(Integer userId, Integer id);

    Optional<UserSearchPresetEntity> findByUserIdAndScopeAndName(Integer userId, SearchPresetScope scope, String name);

    Optional<UserSearchPresetEntity> findByUserIdAndClientId(Integer userId, String clientId);

    int countByUserId(Integer userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserSearchPresetEntity p WHERE p.userId = :userId AND p.id = :id")
    int deleteByUserIdAndId(@Param("userId") Integer userId, @Param("id") Integer id);
}
