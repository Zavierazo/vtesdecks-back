package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entities.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectionRepository extends JpaRepository<Collection, Integer> {
    List<Collection> findByUserIdAndDeletedFalse(Integer userId);
}