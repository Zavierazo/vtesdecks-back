package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CryptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CryptRepository extends JpaRepository<CryptEntity, Integer> {

    @Query(value = "SELECT * FROM crypt WHERE name = :name OR aka = :name", nativeQuery = true)
    CryptEntity selectByName(String name);

}