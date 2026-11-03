package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CryptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface CryptRepository extends JpaRepository<CryptEntity, Integer> {

    @Query("select card.id from CryptEntity card")
    List<Integer> selectIds();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CryptEntity card set card.modificationDate = CURRENT_TIMESTAMP where card.id in :cardIds")
    void touchModificationDate(Collection<Integer> cardIds);

    @Query(value = "SELECT * FROM crypt WHERE name = :name OR aka = :name", nativeQuery = true)
    CryptEntity selectByName(String name);

}