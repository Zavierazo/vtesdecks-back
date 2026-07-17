package com.vtesdecks.jpa.repositories;

import com.vtesdecks.enums.ReactionTargetType;
import com.vtesdecks.jpa.entity.ReactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ReactionRepository extends JpaRepository<ReactionEntity, ReactionEntity.ReactionId> {

    List<ReactionEntity> findByIdTargetTypeAndIdTargetId(ReactionTargetType targetType, String targetId);

    List<ReactionEntity> findByIdTargetTypeAndIdTargetIdIn(ReactionTargetType targetType, Collection<String> targetIds);
}
