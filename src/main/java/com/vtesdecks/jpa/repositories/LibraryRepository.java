package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.LibraryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LibraryRepository extends JpaRepository<LibraryEntity, Integer> {

    @Query(value = "SELECT * FROM library WHERE name = :name OR aka = :name", nativeQuery = true)
    LibraryEntity selectByName(String name);

}