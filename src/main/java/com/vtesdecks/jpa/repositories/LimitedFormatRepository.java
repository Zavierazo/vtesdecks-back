package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.LimitedFormatEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LimitedFormatRepository extends JpaRepository<LimitedFormatEntity, Integer> {
}