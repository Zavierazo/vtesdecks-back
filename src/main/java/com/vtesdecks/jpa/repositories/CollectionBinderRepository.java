package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CollectionBinderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CollectionBinderRepository extends JpaRepository<CollectionBinderEntity, Integer> {
    String PUBLIC_BINDERS = """
            from CollectionBinderEntity b
            join CollectionEntity c on c.id = b.collectionId
            join UserEntity u on u.id = c.userId
            where b.publicVisibility = true and c.deleted = false and b.publicHash is not null
            """;

    @Query("select b " + PUBLIC_BINDERS + " and b.publicHash = :publicHash")
    Optional<CollectionBinderEntity> findPublicByPublicHash(String publicHash);

    @Query("select b.publicHash " + PUBLIC_BINDERS + """
            and exists (select card.id from CollectionCardEntity card
                        where card.binderId = b.id and card.collectionId = c.id and card.number > 0)
            """)
    List<String> findPublicHashesForSitemap();

    List<CollectionBinderEntity> findByCollectionId(Integer collectionId);

    void deleteByCollectionId(Integer collectionId);

    List<CollectionBinderEntity> findByCollectionIdAndPublicVisibilityTrue(Integer collectionId);

    boolean existsByCollectionIdAndNameIgnoreCase(Integer collectionId, String name);

    Optional<CollectionBinderEntity> findByCollectionIdAndNameIgnoreCase(Integer collectionId, String name);

    Optional<CollectionBinderEntity> findByCollectionIdAndId(Integer collectionId, Integer id);

    boolean existsByCollectionIdAndId(Integer collectionId, Integer binderId);


    Optional<CollectionBinderEntity> findByPublicHash(String publicHash);

    boolean existsByPublicHash(String publicHash);
}
