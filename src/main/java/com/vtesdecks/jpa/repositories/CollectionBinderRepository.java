package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CollectionBinderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollectionBinderRepository extends JpaRepository<CollectionBinderEntity, Integer> {
    List<CollectionBinderEntity> findByCollectionId(Integer collectionId);

    boolean existsByCollectionIdAndNameIgnoreCase(Integer collectionId, String name);

    Optional<CollectionBinderEntity> findByCollectionIdAndNameIgnoreCase(Integer collectionId, String name);

    Optional<CollectionBinderEntity> findByCollectionIdAndId(Integer collectionId, Integer id);

    boolean existsByCollectionIdAndId(Integer collectionId, Integer binderId);


    Optional<CollectionBinderEntity> findByPublicHash(String publicHash);

    boolean existsByPublicHash(String publicHash);
}