package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entities.CollectionBinder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollectionBinderRepository extends JpaRepository<CollectionBinder, Integer> {
    List<CollectionBinder> findByCollectionId(Integer collectionId);

    boolean existsByCollectionIdAndNameIgnoreCase(Integer collectionId, String name);

    Optional<CollectionBinder> findByCollectionIdAndNameIgnoreCase(Integer collectionId, String name);

    Optional<CollectionBinder> findByCollectionIdAndId(Integer collectionId, Integer id);

    boolean existsByCollectionIdAndId(Integer collectionId, Integer binderId);


    Optional<CollectionBinder> findByPublicHash(String publicHash);

    boolean existsByPublicHash(String publicHash);
}