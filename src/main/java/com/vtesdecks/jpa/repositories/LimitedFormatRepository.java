package com.vtesdecks.jpa.repositories;

import com.vtesdecks.jpa.entities.LimitedFormat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LimitedFormatRepository extends JpaRepository<LimitedFormat, Integer> {
}