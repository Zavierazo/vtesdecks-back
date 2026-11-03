package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.LibraryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface LibraryRepository extends JpaRepository<LibraryEntity, Integer> {

    @Query("select card.id from LibraryEntity card")
    List<Integer> selectIds();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update LibraryEntity card set card.modificationDate = CURRENT_TIMESTAMP where card.id in :cardIds")
    void touchModificationDate(Collection<Integer> cardIds);

    @Query(value = "SELECT * FROM library WHERE name = :name OR aka = :name", nativeQuery = true)
    LibraryEntity selectByName(String name);

}