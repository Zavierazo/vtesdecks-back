package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.CommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<CommentEntity, Integer> {

    Long countByPageIdentifierAndDeletedFalse(String pageIdentifier);

    List<CommentEntity> findByPageIdentifierOrderByCreationDate(String pageIdentifier);
}