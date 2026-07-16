package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entity.FeatureFlagEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlagEntity, Integer> {

    Optional<FeatureFlagEntity> findByKey(String key);
}
