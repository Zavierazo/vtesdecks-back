package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CollectionCardHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionCardHistoryRepository extends JpaRepository<CollectionCardHistoryEntity, Long> {

    @Query("SELECT h FROM CollectionCardHistoryEntity h WHERE h.collectionId = :collectionId "
            + "AND (:cardId IS NULL OR h.cardId = :cardId) "
            + "AND (:binderId IS NULL OR h.binderId = :binderId) "
            + "ORDER BY h.id DESC")
    Page<CollectionCardHistoryEntity> findHistory(@Param("collectionId") Integer collectionId,
                                                  @Param("cardId") Integer cardId,
                                                  @Param("binderId") Integer binderId,
                                                  Pageable pageable);
}
