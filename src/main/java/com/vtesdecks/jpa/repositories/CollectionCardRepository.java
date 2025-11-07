package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CollectionCardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CollectionCardRepository extends JpaRepository<CollectionCardEntity, Integer> {
    Optional<CollectionCardEntity> findByCollectionIdAndId(Integer collectionId, Integer id);

    List<CollectionCardEntity> findByCollectionIdAndIdIn(Integer collectionId, List<Integer> ids);

    List<CollectionCardEntity> findByCollectionId(Integer collectionId);

    List<CollectionCardEntity> findByCollectionIdAndBinderId(Integer collectionId, Integer binderId);

    List<CollectionCardEntity> findByCollectionIdAndCardId(Integer collectionId, Integer cardId);

    List<CollectionCardEntity> findByCollectionIdAndCardIdIn(Integer id, List<Integer> ids);

    List<CollectionCardEntity> findByCollectionIdAndCardIdAndSetAndConditionAndLanguageAndBinderId(Integer collectionId, Integer cardId, String set, String condition, String language, Integer binderId);

    boolean existsByBinderId(Integer id);

    void deleteByBinderId(Integer id);

    @Modifying
    @Query(value = "UPDATE collection_card c SET c.binder_id = NULL WHERE c.binder_id = :binderId", nativeQuery = true)
    void clearBinderId(@Param("binderId") Integer binderId);

}